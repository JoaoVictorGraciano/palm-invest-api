package com.palminvest.api.infra.cotacao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

// Brapi (https://brapi.dev): busca e cotação de ações, FIIs, ETFs e BDRs da B3
@Component
public class BrapiClient {

    private final RestClient restClient;

    public BrapiClient(@Qualifier("brapiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * GET /quote/list?search=&limit=&type=
     *
     * @param tipo filtro "stock", "fund" ou "bdr" da Brapi; null busca todos
     */
    public List<Ativo> buscar(String termo, String tipo, int limite) {
        try {
            var resposta = restClient.get()
                    .uri(uri -> {
                        uri.path("/quote/list").queryParam("search", termo).queryParam("limit", limite);
                        if (tipo != null) {
                            uri.queryParam("type", tipo);
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(RespostaLista.class);
            return resposta == null || resposta.stocks() == null ? List.of() : resposta.stocks();
        } catch (RestClientException erro) {
            throw ErrosDeProvedor.traduzir(ProvedorExterno.BRAPI, erro);
        }
    }

    /**
     * GET /quote/{simbolo}. Um ativo por chamada: no plano gratuito a Brapi recusa o lote inteiro
     * se um dos ativos exigir plano pago, então em lote um ativo derrubaria os outros.
     *
     * @return vazio se a Brapi não conhece o ativo (404 ou resposta sem resultado)
     */
    public Optional<Cotacao> cotar(String simbolo) {
        try {
            var resposta = restClient.get()
                    .uri("/quote/{simbolo}", simbolo)
                    .retrieve()
                    .body(RespostaCotacao.class);
            if (resposta == null || resposta.results() == null || resposta.results().isEmpty()) {
                return Optional.empty();
            }
            var resultado = resposta.results().getFirst();
            if (resultado.regularMarketPrice() == null) {
                return Optional.empty();
            }
            return Optional.of(new Cotacao(resultado.symbol(), resultado.regularMarketPrice(), resultado.currency(),
                    instanteOuNulo(resultado.regularMarketTime())));
        } catch (HttpClientErrorException.NotFound naoEncontrado) {
            return Optional.empty();
        } catch (RestClientException erro) {
            throw ErrosDeProvedor.traduzir(ProvedorExterno.BRAPI, erro);
        }
    }

    private static Instant instanteOuNulo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(texto);
        } catch (DateTimeParseException formatoDesconhecido) {
            return null;
        }
    }

    // Item de /quote/list; type/subType classificam o ativo (ver AssetSearchService)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ativo(String stock, String name, String type, String subType) {
    }

    // Cotação de um ativo; regularMarketTime é o horário da última negociação informado pela Brapi
    public record Cotacao(String simbolo, BigDecimal preco, String moeda, Instant atualizadoEm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaLista(List<Ativo> stocks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaCotacao(List<ResultadoCotacao> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResultadoCotacao(String symbol, BigDecimal regularMarketPrice, String currency, String regularMarketTime) {
    }
}
