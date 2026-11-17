package com.palminvest.api.service;

import com.palminvest.api.dto.DadosCotacaoDTO;
import com.palminvest.api.dto.DadosCotacoesDTO;
import com.palminvest.api.dto.DadosFalhaCotacaoDTO;
import com.palminvest.api.infra.cotacao.BrapiClient;
import com.palminvest.api.infra.cotacao.CoinGeckoClient;
import com.palminvest.api.infra.cotacao.ProvedorExterno;
import com.palminvest.api.infra.cotacao.ProvedorIndisponivelException;
import com.palminvest.api.infra.cotacao.ProvedoresIndisponiveisException;
import com.palminvest.api.model.AssetClass;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.repository.SimboloClasse;
import com.palminvest.api.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cotação atual em reais de vários símbolos: ações, FIIs, ETFs e BDRs na Brapi (uma chamada por ativo),
 * criptomoedas na CoinGecko (todas numa única chamada). Um provedor fora do ar não derruba o outro:
 * os símbolos dele voltam em failures. Só responde 503 quando nada pôde ser cotado por indisponibilidade.
 */
@Service
public class QuoteService {

    /**
     * Formato de ticker da B3: 4 caracteres começando por letra (B3SA3 tem dígito no meio) + 1 ou 2 dígitos,
     * opcionalmente "F" do fracionário. Só decide o provedor de símbolos que o usuário ainda não registrou;
     * para os que já estão nas transações dele vale a classe gravada.
     */
    static final Pattern TICKER_B3 = Pattern.compile("^[A-Z][A-Z0-9]{3}[0-9]{1,2}F?$");

    private static final String REAL = "BRL";

    private final BrapiClient brapi;
    private final CoinGeckoClient coinGecko;
    private final CoinGeckoIdResolver ids;
    private final TransactionRepository transactions;
    private final ExecutorService executor;

    public QuoteService(BrapiClient brapi, CoinGeckoClient coinGecko, CoinGeckoIdResolver ids,
                        TransactionRepository transactions, @Qualifier("cotacaoExecutor") ExecutorService executor) {
        this.brapi = brapi;
        this.coinGecko = coinGecko;
        this.ids = ids;
        this.transactions = transactions;
        this.executor = executor;
    }

    // simbolos já normalizados (maiúsculas, sem repetição) e validados pelo controller
    public DadosCotacoesDTO cotar(List<String> simbolos, Usuario usuario) {
        var classes = transactions.findClassesDosSimbolos(usuario, simbolos).stream()
                .collect(Collectors.toMap(SimboloClasse::assetSymbol, SimboloClasse::assetClass, (a, b) -> a));

        var daB3 = new ArrayList<String>();
        var criptos = new ArrayList<String>();
        for (var simbolo : simbolos) {
            var classe = classes.get(simbolo);
            boolean ehCripto = classe != null ? classe == AssetClass.CRYPTO : !TICKER_B3.matcher(simbolo).matches();
            (ehCripto ? criptos : daB3).add(simbolo);
        }

        var b3 = emParalelo(() -> cotarNaBrapi(daB3));
        var cripto = emParalelo(() -> cotarCriptos(criptos));
        var resultados = new HashMap<String, Resultado>();
        resultados.putAll(b3.join());
        resultados.putAll(cripto.join());

        var cotacoes = new ArrayList<DadosCotacaoDTO>();
        var falhas = new ArrayList<DadosFalhaCotacaoDTO>();
        var indisponiveis = new ArrayList<ProvedorIndisponivelException>();
        for (var simbolo : simbolos) {
            switch (resultados.get(simbolo)) {
                case Obtida obtida -> cotacoes.add(obtida.cotacao());
                case Falha falha -> {
                    falhas.add(falha.falha());
                    if (falha.causa() != null) {
                        indisponiveis.add(falha.causa());
                    }
                }
            }
        }

        // Nada cotado e todas as falhas são de provedor fora do ar (não de símbolo inexistente): 503
        if (cotacoes.isEmpty() && !falhas.isEmpty() && indisponiveis.size() == falhas.size()) {
            throw new ProvedoresIndisponiveisException(indisponiveis);
        }
        return new DadosCotacoesDTO(cotacoes, falhas);
    }

    private Map<String, Resultado> cotarNaBrapi(List<String> simbolos) {
        var futuros = new LinkedHashMap<String, CompletableFuture<Resultado>>();
        for (var simbolo : simbolos) {
            futuros.put(simbolo, emParalelo(() -> cotarUmaAcao(simbolo)));
        }
        var resultados = new HashMap<String, Resultado>();
        futuros.forEach((simbolo, futuro) -> resultados.put(simbolo, futuro.join()));
        return resultados;
    }

    private Resultado cotarUmaAcao(String simbolo) {
        try {
            return brapi.cotar(simbolo)
                    .<Resultado>map(cotacao -> cotacao.moeda() != null && !REAL.equalsIgnoreCase(cotacao.moeda())
                            ? Falha.naoEncontrada(simbolo, ProvedorExterno.BRAPI, "Cotação de " + simbolo + " não está em reais")
                            : new Obtida(new DadosCotacaoDTO(simbolo, cotacao.preco(), REAL, ProvedorExterno.BRAPI, cotacao.atualizadoEm())))
                    .orElseGet(() -> Falha.naoEncontrada(simbolo, ProvedorExterno.BRAPI, "Cotação não encontrada na Brapi"));
        } catch (ProvedorIndisponivelException indisponivel) {
            return Falha.indisponivel(simbolo, indisponivel);
        }
    }

    private Map<String, Resultado> cotarCriptos(List<String> simbolos) {
        var resultados = new HashMap<String, Resultado>();
        if (simbolos.isEmpty()) {
            return resultados;
        }

        // 1. símbolo -> id da CoinGecko (cada um pode falhar ou não existir)
        var futurosIds = new LinkedHashMap<String, CompletableFuture<String>>();
        for (var simbolo : simbolos) {
            futurosIds.put(simbolo, emParalelo(() -> ids.resolver(simbolo).orElse(null)));
        }
        var idPorSimbolo = new LinkedHashMap<String, String>();
        futurosIds.forEach((simbolo, futuro) -> {
            try {
                var id = futuro.join();
                if (id == null) {
                    resultados.put(simbolo, Falha.naoEncontrada(simbolo, ProvedorExterno.COINGECKO, "Criptomoeda não encontrada na CoinGecko"));
                } else {
                    idPorSimbolo.put(simbolo, id);
                }
            } catch (CompletionException erro) {
                resultados.put(simbolo, Falha.indisponivel(simbolo, indisponivelOuRelanca(erro)));
            }
        });

        // 2. preços de todas as moedas resolvidas numa única chamada
        if (!idPorSimbolo.isEmpty()) {
            try {
                var precos = coinGecko.precosEmReais(idPorSimbolo.values().stream().distinct().toList());
                idPorSimbolo.forEach((simbolo, id) -> {
                    var preco = precos.get(id);
                    resultados.put(simbolo, preco == null
                            ? Falha.naoEncontrada(simbolo, ProvedorExterno.COINGECKO, "Cotação não encontrada na CoinGecko")
                            : new Obtida(new DadosCotacaoDTO(simbolo, preco.emReais(), REAL, ProvedorExterno.COINGECKO, preco.atualizadoEm())));
                });
            } catch (ProvedorIndisponivelException indisponivel) {
                idPorSimbolo.keySet().forEach(simbolo -> resultados.put(simbolo, Falha.indisponivel(simbolo, indisponivel)));
            }
        }
        return resultados;
    }

    private static ProvedorIndisponivelException indisponivelOuRelanca(CompletionException erro) {
        if (erro.getCause() instanceof ProvedorIndisponivelException indisponivel) {
            return indisponivel;
        }
        if (erro.getCause() instanceof RuntimeException inesperado) {
            throw inesperado;
        }
        throw erro;
    }

    private <T> CompletableFuture<T> emParalelo(Supplier<T> tarefa) {
        return CompletableFuture.supplyAsync(tarefa, executor);
    }

    private sealed interface Resultado permits Obtida, Falha {
    }

    private record Obtida(DadosCotacaoDTO cotacao) implements Resultado {
    }

    // causa != null: provedor indisponível; causa == null: símbolo não encontrado (o provedor respondeu)
    private record Falha(DadosFalhaCotacaoDTO falha, ProvedorIndisponivelException causa) implements Resultado {
        static Falha naoEncontrada(String simbolo, ProvedorExterno provedor, String mensagem) {
            return new Falha(new DadosFalhaCotacaoDTO(simbolo, provedor, mensagem), null);
        }

        static Falha indisponivel(String simbolo, ProvedorIndisponivelException causa) {
            return new Falha(new DadosFalhaCotacaoDTO(simbolo, causa.provedor(), causa.getMessage()), causa);
        }
    }
}
