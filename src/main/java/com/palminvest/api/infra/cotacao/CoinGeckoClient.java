package com.palminvest.api.infra.cotacao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// CoinGecko (https://www.coingecko.com): busca e cotação de criptomoedas
@Component
public class CoinGeckoClient {

    private static final ParameterizedTypeReference<Map<String, Map<String, BigDecimal>>> TIPO_PRECOS =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public CoinGeckoClient(@Qualifier("coingeckoRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // GET /search?query=: moedas na ordem da CoinGecko (valor de mercado)
    public List<Moeda> buscar(String termo) {
        try {
            var resposta = restClient.get()
                    .uri(uri -> uri.path("/search").queryParam("query", termo).build())
                    .retrieve()
                    .body(RespostaBusca.class);
            return resposta == null || resposta.coins() == null ? List.of() : resposta.coins();
        } catch (RestClientException erro) {
            throw ErrosDeProvedor.traduzir(ProvedorExterno.COINGECKO, erro);
        }
    }

    /**
     * GET /simple/price: preço em reais de várias moedas numa única chamada.
     *
     * @return por id da CoinGecko; ids sem preço ficam de fora
     */
    public Map<String, Preco> precosEmReais(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            var resposta = restClient.get()
                    .uri(uri -> uri.path("/simple/price")
                            .queryParam("ids", String.join(",", ids))
                            .queryParam("vs_currencies", "brl")
                            .queryParam("include_last_updated_at", true)
                            .build())
                    .retrieve()
                    .body(TIPO_PRECOS);
            var precos = new HashMap<String, Preco>();
            if (resposta != null) {
                resposta.forEach((id, valores) -> {
                    var brl = valores == null ? null : valores.get("brl");
                    if (brl != null) {
                        var atualizado = valores.get("last_updated_at");
                        precos.put(id, new Preco(brl, atualizado == null ? null : Instant.ofEpochSecond(atualizado.longValue())));
                    }
                });
            }
            return precos;
        } catch (RestClientException erro) {
            throw ErrosDeProvedor.traduzir(ProvedorExterno.COINGECKO, erro);
        }
    }

    // Item de /search; várias moedas podem ter o mesmo símbolo (ver AssetSearchService)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Moeda(String id, String name, String symbol, Integer market_cap_rank) {
    }

    public record Preco(BigDecimal emReais, Instant atualizadoEm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaBusca(List<Moeda> coins) {
    }
}
