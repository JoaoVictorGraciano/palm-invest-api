package com.palminvest.api.security;

import com.palminvest.api.IntegracaoTestBase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SegurancaJwtTest extends IntegracaoTestBase {

    private static final String ISSUER = "API Palm Invest";
    private static final String ROTA_PROTEGIDA = "/api/transactions";

    // --- JWT ---

    @Test
    void semHeaderAuthorizationRetorna401() throws Exception {
        mockMvc.perform(get(ROTA_PROTEGIDA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoRetorna200() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");

        mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void tokenAdulteradoRetorna401() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        cadastrarUsuario("B", "b@exemplo.com");

        // Troca o payload para se passar por B, mantendo a assinatura original do token de A
        var partes = tokenA.split("\\.");
        var payloadFalso = Map.of(
                "iss", ISSUER,
                "sub", "b@exemplo.com",
                "exp", Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond());
        var payloadCodificado = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jsonMapper.writeValueAsString(payloadFalso).getBytes(StandardCharsets.UTF_8));
        var tokenAdulterado = partes[0] + "." + payloadCodificado + "." + partes[2];

        mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", "Bearer " + tokenAdulterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAssinadoComOutraChaveRetorna401() throws Exception {
        cadastrarUsuario("A", "a@exemplo.com");

        byte[] outraChave = new byte[32];
        new SecureRandom().nextBytes(outraChave);
        var token = gerarToken(Keys.hmacShaKeyFor(outraChave), "a@exemplo.com",
                Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        cadastrarUsuario("A", "a@exemplo.com");

        // Assinado com a chave certa, mas vencido há 1 minuto
        var chaveDaAplicacao = Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET_TESTE));
        var token = gerarToken(chaveDaAplicacao, "a@exemplo.com", Instant.now().minus(1, ChronoUnit.MINUTES));

        mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSemPrefixoBearerRetorna401() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");

        mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    // --- CORS ---

    @Test
    void preflightDoFrontLocalhost5173EhPermitido() throws Exception {
        mockMvc.perform(options(ROTA_PROTEGIDA)
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflightDeOrigemDesconhecidaRetorna403() throws Exception {
        mockMvc.perform(options(ROTA_PROTEGIDA)
                        .header("Origin", "http://evil.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private String gerarToken(SecretKey chave, String subject, Instant expiracao) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(Date.from(expiracao.minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(expiracao))
                .signWith(chave)
                .compact();
    }
}
