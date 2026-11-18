package com.palminvest.api.infra.cotacao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

// Brapi (https://brapi.dev): busca e cotação de ações, FIIs, ETFs e BDRs da B3
@Component
public class BrapiClient {

    // Tentativas por chamada quando a Brapi responde 429 (a primeira + 2 repetições)
    private static final int TENTATIVAS = 3;
    private static final Duration ESPERA_BASE = Duration.ofMillis(600);
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(3);

    private final RestClient restClient;
    // A Brapi recusa rajadas de chamadas simultâneas: no máximo N em andamento (BRAPI_MAX_PARALELAS)
    private final Semaphore paralelas;

    public BrapiClient(@Qualifier("brapiRestClient") RestClient restClient, CotacaoProperties properties) {
        this.restClient = restClient;
        this.paralelas = new Semaphore(properties.brapi().limiteDeParalelas(), true);
    }

    /**
     * GET /quote/list?search=&limit=&type=
     *
     * @param tipo filtro "stock", "fund" ou "bdr" da Brapi; null busca todos
     */
    public List<Ativo> buscar(String termo, String tipo, int limite) {
        try {
            var resposta = comLimite(() -> restClient.get()
                    .uri(uri -> {
                        uri.path("/quote/list").queryParam("search", termo).queryParam("limit", limite);
                        if (tipo != null) {
                            uri.queryParam("type", tipo);
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(RespostaLista.class));
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
            var resposta = comLimite(() -> restClient.get()
                    .uri("/quote/{simbolo}", simbolo)
                    .retrieve()
                    .body(RespostaCotacao.class));
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

    /**
     * Executa a chamada respeitando o limite de chamadas simultâneas e repete quando a Brapi responde 429,
     * esperando o Retry-After (se vier, até 3 s) ou um tempo crescente. Outros erros sobem na hora.
     */
    private <T> T comLimite(Supplier<T> chamada) {
        try {
            paralelas.acquire();
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            throw new ProvedorIndisponivelException(ProvedorExterno.BRAPI, "Consulta à Brapi interrompida", interrompido);
        }
        try {
            for (int tentativa = 1; ; tentativa++) {
                try {
                    return chamada.get();
                } catch (HttpClientErrorException.TooManyRequests limite) {
                    if (tentativa >= TENTATIVAS) {
                        throw limite;
                    }
                    esperar(esperaAntesDe(tentativa, limite));
                }
            }
        } finally {
            paralelas.release();
        }
    }

    private static Duration esperaAntesDe(int tentativa, HttpClientErrorException.TooManyRequests limite) {
        var retryAfter = limite.getResponseHeaders() == null ? null : limite.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null && retryAfter.matches("[0-9]+")) {
            var pedida = Duration.ofSeconds(Long.parseLong(retryAfter));
            return pedida.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : pedida;
        }
        return ESPERA_BASE.multipliedBy(tentativa);
    }

    private static void esperar(Duration duracao) {
        try {
            Thread.sleep(duracao);
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            throw new ProvedorIndisponivelException(ProvedorExterno.BRAPI, "Consulta à Brapi interrompida", interrompido);
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
