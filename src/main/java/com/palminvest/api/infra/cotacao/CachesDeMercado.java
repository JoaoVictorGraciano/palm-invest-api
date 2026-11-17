package com.palminvest.api.infra.cotacao;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.palminvest.api.dto.DadosSugestaoAtivoDTO;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Caches em memória dos provedores de mercado, compartilhados entre todos os usuários (uma consulta de um
 * usuário serve aos demais). Só guardam respostas do provedor: falhas (timeout, limite, 5xx) nunca ficam no
 * cache, para a próxima requisição tentar de novo. Tempos em api.cotacoes.cache (variáveis CACHE_*).
 */
@Component
public class CachesDeMercado {

    // Teto de itens por cache, só para memória não crescer sem limite
    private static final long MAXIMO_ITENS = 10_000;

    // Cotação da Brapi por símbolo; Optional.empty = a Brapi não conhece o ativo (evita repetir a consulta)
    private final Cache<String, Optional<BrapiClient.Cotacao>> cotacaoAcoes;
    // Preço em reais por id da CoinGecko
    private final Cache<String, CoinGeckoClient.Preco> cotacaoCripto;
    // Sugestões por provedor e termo (+ classe na Brapi)
    private final Cache<String, List<DadosSugestaoAtivoDTO>> buscas;
    // Símbolo -> id da CoinGecko; "não existe" vale só pelo tempo da busca, para moedas recém-listadas aparecerem logo
    private final Cache<String, Optional<String>> idCripto;

    public CachesDeMercado(CotacaoProperties properties) {
        var tempos = properties.cache();
        this.cotacaoAcoes = Caffeine.newBuilder().maximumSize(MAXIMO_ITENS).expireAfterWrite(tempos.cotacaoAcoes()).build();
        this.cotacaoCripto = Caffeine.newBuilder().maximumSize(MAXIMO_ITENS).expireAfterWrite(tempos.cotacaoCripto()).build();
        this.buscas = Caffeine.newBuilder().maximumSize(MAXIMO_ITENS).expireAfterWrite(tempos.busca()).build();
        this.idCripto = Caffeine.newBuilder().maximumSize(MAXIMO_ITENS)
                .expireAfter(Expiry.<String, Optional<String>>writing((simbolo, id) -> id.isPresent() ? tempos.idCripto() : tempos.busca()))
                .build();
    }

    public Optional<Optional<BrapiClient.Cotacao>> cotacaoAcao(String simbolo) {
        return Optional.ofNullable(cotacaoAcoes.getIfPresent(simbolo));
    }

    public void guardarCotacaoAcao(String simbolo, Optional<BrapiClient.Cotacao> cotacao) {
        cotacaoAcoes.put(simbolo, cotacao);
    }

    public Map<String, CoinGeckoClient.Preco> precosCripto(Collection<String> ids) {
        return cotacaoCripto.getAllPresent(ids);
    }

    public void guardarPrecosCripto(Map<String, CoinGeckoClient.Preco> precos) {
        cotacaoCripto.putAll(precos);
    }

    public Optional<List<DadosSugestaoAtivoDTO>> busca(ProvedorExterno provedor, String chave) {
        return Optional.ofNullable(buscas.getIfPresent(provedor.id() + "|" + chave));
    }

    public void guardarBusca(ProvedorExterno provedor, String chave, List<DadosSugestaoAtivoDTO> sugestoes) {
        buscas.put(provedor.id() + "|" + chave, List.copyOf(sugestoes));
    }

    public Optional<Optional<String>> idCripto(String simbolo) {
        return Optional.ofNullable(idCripto.getIfPresent(simbolo));
    }

    public void guardarIdCripto(String simbolo, Optional<String> id) {
        idCripto.put(simbolo, id);
    }

    // Só para os testes começarem sem nada em memória
    public void limparTudo() {
        cotacaoAcoes.invalidateAll();
        cotacaoCripto.invalidateAll();
        buscas.invalidateAll();
        idCripto.invalidateAll();
    }

    // Usado no log de subida, para conferir os tempos efetivos
    public static String descrever(CotacaoProperties.Cache tempos) {
        return "ações %s, cripto %s, buscas %s, id de cripto %s".formatted(
                legivel(tempos.cotacaoAcoes()), legivel(tempos.cotacaoCripto()), legivel(tempos.busca()), legivel(tempos.idCripto()));
    }

    private static String legivel(Duration duracao) {
        return duracao.toString().substring(2).toLowerCase();
    }
}
