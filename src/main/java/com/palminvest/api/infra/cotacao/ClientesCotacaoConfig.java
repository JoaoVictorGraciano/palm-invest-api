package com.palminvest.api.infra.cotacao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Um RestClient por provedor, com timeouts e a credencial (se houver) já no header padrão.
// Credencial em header, e não na URL, para nunca aparecer em mensagens de erro ou logs de acesso.
@Configuration
@EnableConfigurationProperties(CotacaoProperties.class)
public class ClientesCotacaoConfig {

    @Bean
    public RestClient brapiRestClient(RestClient.Builder builder, CotacaoProperties properties, ConsumoDeProvedores consumo) {
        var brapi = properties.brapi();
        var cliente = builder.clone()
                .baseUrl(brapi.url())
                .requestFactory(fabricaComTimeouts(properties))
                .requestInterceptor(consumo.interceptor(ProvedorExterno.BRAPI));
        if (brapi.temToken()) {
            cliente.defaultHeader("Authorization", "Bearer " + brapi.token());
        }
        return cliente.build();
    }

    @Bean
    public RestClient coingeckoRestClient(RestClient.Builder builder, CotacaoProperties properties, ConsumoDeProvedores consumo) {
        var coingecko = properties.coingecko();
        var cliente = builder.clone()
                .baseUrl(coingecko.url())
                .requestFactory(fabricaComTimeouts(properties))
                .requestInterceptor(consumo.interceptor(ProvedorExterno.COINGECKO));
        if (coingecko.temApiKey()) {
            // Chave do plano Demo da CoinGecko
            cliente.defaultHeader("x-cg-demo-api-key", coingecko.apiKey());
        }
        return cliente.build();
    }

    // Consulta Brapi e CoinGecko em paralelo (e várias cotações da Brapi ao mesmo tempo) sem prender threads da web
    @Bean(destroyMethod = "close")
    public ExecutorService cotacaoExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    private static JdkClientHttpRequestFactory fabricaComTimeouts(CotacaoProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeoutConexao())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        var fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(properties.timeoutLeitura());
        return fabrica;
    }
}
