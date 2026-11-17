package com.palminvest.api.infra.cotacao;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Configuração dos provedores externos (prefixo api.cotacoes em application.properties).
// Token e chave vêm de BRAPI_TOKEN e COINGECKO_API_KEY; vazios significam "usar o limite gratuito".
@ConfigurationProperties("api.cotacoes")
public record CotacaoProperties(
        Provedor brapi,
        CoinGecko coingecko,
        Duration timeoutConexao,
        Duration timeoutLeitura
) {
    public record Provedor(String url, String token) {
        public boolean temToken() {
            return token != null && !token.isBlank();
        }
    }

    public record CoinGecko(String url, String apiKey) {
        public boolean temApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    // toString padrão do record imprimiria o token/chave: nunca deixar isso chegar a um log
    @Override
    public String toString() {
        return "CotacaoProperties[brapi=" + brapi.url() + (brapi.temToken() ? " (com token)" : " (sem token)")
                + ", coingecko=" + coingecko.url() + (coingecko.temApiKey() ? " (com chave)" : " (sem chave)") + "]";
    }
}
