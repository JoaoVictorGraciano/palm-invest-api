package com.palminvest.api.service;

import com.palminvest.api.dto.DadosBuscaAtivosDTO;
import com.palminvest.api.dto.DadosSugestaoAtivoDTO;
import com.palminvest.api.infra.cotacao.BrapiClient;
import com.palminvest.api.infra.cotacao.CachesDeMercado;
import com.palminvest.api.infra.cotacao.CoinGeckoClient;
import com.palminvest.api.infra.cotacao.ProvedorExterno;
import com.palminvest.api.infra.cotacao.ProvedorIndisponivelException;
import com.palminvest.api.infra.cotacao.ProvedoresIndisponiveisException;
import com.palminvest.api.model.AssetClass;
import com.palminvest.api.model.AssetClassFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Busca de ativos nos provedores de mercado, com as mesmas regras que o front aplicava no navegador:
 * - Brapi (/quote/list): ações, FIIs, ETFs e BDRs, classificados por type/subType;
 * - CoinGecko (/search): criptomoedas, uma por símbolo (a de maior valor de mercado);
 * - ordenação pelo casamento com o texto digitado, preservando a ordem do provedor nos empates.
 */
@Service
public class AssetSearchService {

    // Quantas sugestões cada provedor contribui, no máximo
    static final int MAX_POR_PROVEDOR = 6;

    // Busca de uma classe só: o filtro "fund" da Brapi mistura FII e ETF, então vem uma página maior, filtrada aqui
    static final int PAGINA_BRAPI_FILTRADA = 30;

    // Formato aceito em assetSymbol (até 20 caracteres, sem espaços)
    static final Pattern SIMBOLO_VALIDO = Pattern.compile("^[A-Z0-9]{1,20}$");

    private final BrapiClient brapi;
    private final CoinGeckoClient coinGecko;
    private final CachesDeMercado caches;
    private final ExecutorService executor;

    public AssetSearchService(BrapiClient brapi, CoinGeckoClient coinGecko, CachesDeMercado caches,
                              @Qualifier("cotacaoExecutor") ExecutorService executor) {
        this.brapi = brapi;
        this.coinGecko = coinGecko;
        this.caches = caches;
        this.executor = executor;
    }

    /**
     * Sem filtro (ALL), consulta os dois provedores ao mesmo tempo; com uma classe, só o provedor dela.
     * Um provedor que falha aparece em failedProviders; se todos os consultados falharem, lança
     * ProvedoresIndisponiveisException (503).
     */
    public DadosBuscaAtivosDTO buscar(String texto, AssetClassFilter filtro) {
        var termo = texto.trim().toUpperCase(Locale.ROOT);
        var classe = filtro.classe().orElse(null);
        boolean querB3 = classe == null || classe != AssetClass.CRYPTO;
        boolean querCripto = classe == null || classe == AssetClass.CRYPTO;

        var b3 = querB3 ? emParalelo(() -> comCache(ProvedorExterno.BRAPI, termo + "|" + (classe == null ? "ALL" : classe),
                () -> buscarNaBrapi(termo, classe))) : null;
        var cripto = querCripto ? emParalelo(() -> comCache(ProvedorExterno.COINGECKO, termo, () -> buscarCriptos(termo))) : null;

        var sugestoes = new ArrayList<DadosSugestaoAtivoDTO>();
        var falhas = new ArrayList<ProvedorIndisponivelException>();
        coletar(b3, sugestoes, falhas);
        coletar(cripto, sugestoes, falhas);

        if (sugestoes.isEmpty() && !falhas.isEmpty() && falhas.size() == (querB3 ? 1 : 0) + (querCripto ? 1 : 0)) {
            throw new ProvedoresIndisponiveisException(falhas);
        }
        return new DadosBuscaAtivosDTO(ordenarPorCasamento(termo, sugestoes, DadosSugestaoAtivoDTO::symbol, DadosSugestaoAtivoDTO::name),
                falhas.stream().map(ProvedorIndisponivelException::provedor).toList());
    }

    List<DadosSugestaoAtivoDTO> buscarNaBrapi(String termo, AssetClass classe) {
        var tipo = classe == null ? null : tipoBrapi(classe);
        var limite = classe == null ? MAX_POR_PROVEDOR : PAGINA_BRAPI_FILTRADA;

        return brapi.buscar(termo, tipo, limite).stream()
                .map(ativo -> {
                    var simbolo = ativo.stock() == null ? "" : ativo.stock().trim().toUpperCase(Locale.ROOT);
                    var nome = ativo.name() == null || ativo.name().isBlank() ? simbolo : ativo.name().trim();
                    return new DadosSugestaoAtivoDTO(simbolo, nome, classificarAtivoBrapi(ativo.type(), ativo.subType()));
                })
                .filter(s -> SIMBOLO_VALIDO.matcher(s.symbol()).matches() && (classe == null || s.assetClass() == classe))
                .limit(MAX_POR_PROVEDOR)
                .toList();
    }

    List<DadosSugestaoAtivoDTO> buscarCriptos(String termo) {
        var melhores = melhorMoedaPorSimbolo(coinGecko.buscar(termo));
        // Cada busca já ensina o id de cada símbolo: cotar a moeda escolhida depois não gasta outra chamada
        melhores.forEach((simbolo, moeda) -> caches.guardarIdCripto(simbolo, Optional.of(moeda.id())));

        // Maior valor de mercado primeiro; depois o casamento com o texto. Só então o corte,
        // para um símbolo exato de moeda pequena não ficar de fora.
        var porValorDeMercado = melhores.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> ranking(e.getValue())))
                .map(e -> new DadosSugestaoAtivoDTO(e.getKey(), e.getValue().name(), AssetClass.CRYPTO))
                .toList();
        return ordenarPorCasamento(termo, porValorDeMercado, DadosSugestaoAtivoDTO::symbol, DadosSugestaoAtivoDTO::name)
                .stream().limit(MAX_POR_PROVEDOR).toList();
    }

    /**
     * Classe de um ativo da Brapi a partir de type/subType de /quote/list:
     *
     * | type  | subType                              | classe |
     * |-------|--------------------------------------|--------|
     * | stock | stock, unit                          | STOCK  |
     * | bdr   | bdr                                  | BDR    |
     * | fund  | etf                                  | ETF    |
     * | fund  | fii                                  | FII    |
     * | fund  | fi-agro, fi-infra, fip, fidc, (nulo) | FII    |
     *
     * Fiagro, FI-Infra, FIP e FIDC não têm classe própria; ficam como FII, a mais próxima
     * (fundos fechados negociados em bolsa, e não fundos de índice).
     */
    public static AssetClass classificarAtivoBrapi(String type, String subType) {
        var tipo = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        var subtipo = subType == null ? "" : subType.trim().toLowerCase(Locale.ROOT);

        if (tipo.equals("bdr") || subtipo.equals("bdr")) return AssetClass.BDR;
        if (subtipo.equals("etf")) return AssetClass.ETF;
        if (tipo.equals("fund") || subtipo.equals("fii")) return AssetClass.FII;
        return AssetClass.STOCK;
    }

    /**
     * Na CoinGecko várias moedas compartilham o mesmo símbolo, mas a carteira guarda só o símbolo.
     * Fica uma moeda por símbolo: a de maior valor de mercado (menor market_cap_rank; sem ranking vai para o fim).
     */
    public static Map<String, CoinGeckoClient.Moeda> melhorMoedaPorSimbolo(List<CoinGeckoClient.Moeda> moedas) {
        var porSimbolo = new LinkedHashMap<String, CoinGeckoClient.Moeda>();
        for (var moeda : moedas) {
            var simbolo = moeda.symbol() == null ? "" : moeda.symbol().trim().toUpperCase(Locale.ROOT);
            if (moeda.id() == null || !SIMBOLO_VALIDO.matcher(simbolo).matches()) {
                continue;
            }
            var atual = porSimbolo.get(simbolo);
            if (atual == null || ranking(moeda) < ranking(atual)) {
                porSimbolo.put(simbolo, moeda);
            }
        }
        return porSimbolo;
    }

    /**
     * Ordena pelo casamento com o texto; menor é melhor:
     * 0. símbolo igual ao texto;
     * 1. símbolo ou nome começando com o texto;
     * 2. o resto.
     * O nome conta no nível 1 para "BI" trazer o Bitcoin (BTC) antes de moedas pequenas cujo símbolo começa
     * com "BI". A ordenação é estável: empates mantêm a ordem do provedor (nas criptos, valor de mercado).
     */
    static <T> List<T> ordenarPorCasamento(String termo, List<T> itens, Function<T, String> simbolo, Function<T, String> nome) {
        var alvo = termo.trim().toUpperCase(Locale.ROOT);
        var ordenados = new ArrayList<>(itens);
        ordenados.sort(Comparator.comparingInt(item -> {
            var s = simbolo.apply(item).toUpperCase(Locale.ROOT);
            if (s.equals(alvo)) return 0;
            return s.startsWith(alvo) || nome.apply(item).toUpperCase(Locale.ROOT).startsWith(alvo) ? 1 : 2;
        }));
        return ordenados;
    }

    private static int ranking(CoinGeckoClient.Moeda moeda) {
        return moeda.market_cap_rank() == null ? Integer.MAX_VALUE : moeda.market_cap_rank();
    }

    // Valor do parâmetro type da Brapi que contém cada classe da B3
    private static String tipoBrapi(AssetClass classe) {
        return switch (classe) {
            case STOCK -> "stock";
            case FII, ETF -> "fund";
            case BDR -> "bdr";
            case CRYPTO -> throw new IllegalArgumentException("CRYPTO não é buscada na Brapi");
        };
    }

    // Resultado de um provedor para o termo, do cache se houver; falha do provedor não entra no cache
    private List<DadosSugestaoAtivoDTO> comCache(ProvedorExterno provedor, String chave, Supplier<List<DadosSugestaoAtivoDTO>> consulta) {
        return caches.busca(provedor, chave).orElseGet(() -> {
            var sugestoes = consulta.get();
            caches.guardarBusca(provedor, chave, sugestoes);
            return sugestoes;
        });
    }

    private <T> CompletableFuture<T> emParalelo(Supplier<T> tarefa) {
        return CompletableFuture.supplyAsync(tarefa, executor);
    }

    private static void coletar(CompletableFuture<List<DadosSugestaoAtivoDTO>> futuro,
                                List<DadosSugestaoAtivoDTO> sugestoes, List<ProvedorIndisponivelException> falhas) {
        if (futuro == null) {
            return;
        }
        try {
            sugestoes.addAll(futuro.join());
        } catch (CompletionException erro) {
            if (erro.getCause() instanceof ProvedorIndisponivelException indisponivel) {
                falhas.add(indisponivel);
            } else if (erro.getCause() instanceof RuntimeException inesperado) {
                throw inesperado;
            } else {
                throw erro;
            }
        }
    }
}
