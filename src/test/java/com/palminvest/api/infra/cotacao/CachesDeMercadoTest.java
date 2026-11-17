package com.palminvest.api.infra.cotacao;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Os tempos de cache vêm da configuração (CACHE_*), e "moeda não existe" expira antes de um id encontrado
class CachesDeMercadoTest {

    @Test
    void cadaCacheExpiraNoTempoConfigurado() throws InterruptedException {
        var caches = new CachesDeMercado(propriedades(Duration.ofMillis(150), Duration.ofHours(1), Duration.ofMillis(150), Duration.ofHours(1)));
        var cotacao = new BrapiClient.Cotacao("PETR4", new BigDecimal("38.25"), "BRL", null);
        caches.guardarCotacaoAcao("PETR4", Optional.of(cotacao));
        caches.guardarPrecosCripto(Map.of("bitcoin", new CoinGeckoClient.Preco(new BigDecimal("1"), null)));

        assertEquals(Optional.of(cotacao), caches.cotacaoAcao("PETR4").orElseThrow());
        Thread.sleep(400);

        assertTrue(caches.cotacaoAcao("PETR4").isEmpty(), "cotação de ação deveria ter expirado");
        assertEquals(1, caches.precosCripto(List.of("bitcoin")).size(), "cotação de cripto ainda vale");
    }

    @Test
    void idInexistenteExpiraNoTempoDaBuscaEIdEncontradoNoTempoDoMapa() throws InterruptedException {
        var caches = new CachesDeMercado(propriedades(Duration.ofHours(1), Duration.ofHours(1), Duration.ofMillis(150), Duration.ofHours(24)));
        caches.guardarIdCripto("ENA", Optional.of("ethena"));
        caches.guardarIdCripto("ZZZQ", Optional.empty());
        Thread.sleep(400);

        assertEquals(Optional.of("ethena"), caches.idCripto("ENA").orElseThrow());
        assertTrue(caches.idCripto("ZZZQ").isEmpty(), "\"não existe\" deveria valer só pelo tempo da busca");
    }

    private static CotacaoProperties propriedades(Duration acoes, Duration cripto, Duration busca, Duration idCripto) {
        return new CotacaoProperties(
                new CotacaoProperties.Provedor("http://127.0.0.1:1", "", 2),
                new CotacaoProperties.CoinGecko("http://127.0.0.1:1", ""),
                Duration.ofSeconds(1), Duration.ofSeconds(1),
                new CotacaoProperties.Cache(acoes, cripto, busca, idCripto));
    }
}
