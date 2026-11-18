package com.palminvest.api.infra.cotacao;

import com.palminvest.api.ProvedoresFalsos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

import static com.palminvest.api.RespostasDeMercado.buscaCoinGecko;
import static com.palminvest.api.RespostasDeMercado.cotacaoBrapi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Token e chave são opcionais: sem eles nenhum header de credencial sai; com eles, só em header (nunca na URL)
class ClientesCotacaoConfigTest {

    private final ProvedoresFalsos provedores = ProvedoresFalsos.iniciar();

    @AfterEach
    void limpar() {
        provedores.limpar();
    }

    @Test
    void semTokenNemChaveNaoEnviaHeaderDeCredencial() {
        var config = new ClientesCotacaoConfig();
        var properties = propriedades("", "");
        var consumo = new ConsumoDeProvedores(properties);
        provedores.quando("/brapi/quote/PETR4", 200, cotacaoBrapi("PETR4", "38.25", "2026-11-17T17:30:00.000Z"));
        provedores.quando("/coingecko/search", 200, buscaCoinGecko());

        new BrapiClient(config.brapiRestClient(RestClient.builder(), properties, consumo), properties).cotar("PETR4");
        new CoinGeckoClient(config.coingeckoRestClient(RestClient.builder(), properties, consumo)).buscar("BTC");

        var brapi = provedores.requisicoes("/brapi/quote/PETR4").getFirst();
        var coingecko = provedores.requisicoes("/coingecko/search").getFirst();
        assertFalse(brapi.headers().containsKey("authorization"));
        assertFalse(coingecko.headers().containsKey("x-cg-demo-api-key"));
        assertEquals(1, consumo.chamadas(ProvedorExterno.BRAPI));
        assertEquals(1, consumo.chamadas(ProvedorExterno.COINGECKO));
    }

    @Test
    void comTokenEChaveEnviaSoEmHeaderENuncaNaUrl() {
        var token = UUID.randomUUID().toString();
        var chave = UUID.randomUUID().toString();
        var config = new ClientesCotacaoConfig();
        var properties = propriedades(token, chave);
        var consumo = new ConsumoDeProvedores(properties);
        provedores.quando("/brapi/quote/list", 200, "{\"stocks\":[]}");
        provedores.quando("/coingecko/search", 200, buscaCoinGecko());

        new BrapiClient(config.brapiRestClient(RestClient.builder(), properties, consumo), properties).buscar("PETR", null, 6);
        new CoinGeckoClient(config.coingeckoRestClient(RestClient.builder(), properties, consumo)).buscar("BTC");

        var brapi = provedores.requisicoes("/brapi/quote/list").getFirst();
        var coingecko = provedores.requisicoes("/coingecko/search").getFirst();
        assertEquals("Bearer " + token, brapi.headers().get("authorization"));
        assertEquals(chave, coingecko.headers().get("x-cg-demo-api-key"));
        assertFalse(brapi.query().containsValue(token));
        assertFalse(coingecko.query().containsValue(chave));
        assertTrue(properties.toString().contains("com token") && !properties.toString().contains(token));
    }

    private CotacaoProperties propriedades(String token, String chave) {
        return new CotacaoProperties(
                new CotacaoProperties.Provedor(provedores.url("/brapi"), token, 2),
                new CotacaoProperties.CoinGecko(provedores.url("/coingecko"), chave),
                Duration.ofSeconds(1), Duration.ofSeconds(2),
                new CotacaoProperties.Cache(Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofHours(24)));
    }

}
