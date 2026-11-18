package com.palminvest.api.infra.cotacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Conta e registra em log cada chamada HTTP real feita à Brapi e à CoinGecko (acertos de cache não contam),
 * para acompanhar o consumo da cota de cada provedor. Registra método, caminho, status e tempo, nunca headers:
 * token e chave vão em header e não aparecem. Por garantia, qualquer ocorrência deles no texto é mascarada.
 */
@Component
public class ConsumoDeProvedores {

    private static final Logger log = LoggerFactory.getLogger(ConsumoDeProvedores.class);

    private final Map<ProvedorExterno, AtomicLong> chamadas = new EnumMap<>(ProvedorExterno.class);
    private final CotacaoProperties properties;

    public ConsumoDeProvedores(CotacaoProperties properties) {
        this.properties = properties;
        for (var provedor : ProvedorExterno.values()) {
            chamadas.put(provedor, new AtomicLong());
        }
    }

    // Interceptor do RestClient de um provedor: conta antes de chamar, para falhas de rede também contarem
    public ClientHttpRequestInterceptor interceptor(ProvedorExterno provedor) {
        return (requisicao, corpo, execucao) -> {
            long total = chamadas.get(provedor).incrementAndGet();
            var destino = mascarar(requisicao.getMethod() + " " + requisicao.getURI().getRawPath()
                    + (requisicao.getURI().getRawQuery() == null ? "" : "?" + requisicao.getURI().getRawQuery()));
            long inicio = System.nanoTime();
            try {
                var resposta = execucao.execute(requisicao, corpo);
                log.info("Chamada à {} nº {}: {} -> HTTP {} em {} ms | {}", provedor.nome(), total, destino,
                        resposta.getStatusCode().value(), (System.nanoTime() - inicio) / 1_000_000, resumo());
                return resposta;
            } catch (IOException falha) {
                log.warn("Chamada à {} nº {}: {} -> falhou ({}) em {} ms | {}", provedor.nome(), total, destino,
                        falha.getClass().getSimpleName(), (System.nanoTime() - inicio) / 1_000_000, resumo());
                throw falha;
            }
        };
    }

    public long chamadas(ProvedorExterno provedor) {
        return chamadas.get(provedor).get();
    }

    // "total desde a subida: Brapi 12, CoinGecko 4"
    public String resumo() {
        var texto = new StringBuilder("total desde a subida: ");
        for (var provedor : ProvedorExterno.values()) {
            if (provedor.ordinal() > 0) {
                texto.append(", ");
            }
            texto.append(provedor.nome()).append(' ').append(chamadas.get(provedor).get());
        }
        return texto.toString();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registrarConfiguracao() {
        log.info("Provedores de mercado: {}; cache: {}", properties, CachesDeMercado.descrever(properties.cache()));
    }

    private String mascarar(String texto) {
        var resultado = texto;
        if (properties.brapi().temToken()) {
            resultado = resultado.replace(properties.brapi().token(), "***");
        }
        if (properties.coingecko().temApiKey()) {
            resultado = resultado.replace(properties.coingecko().apiKey(), "***");
        }
        return resultado;
    }
}
