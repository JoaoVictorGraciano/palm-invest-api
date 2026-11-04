package com.palminvest.api.security;

import com.palminvest.api.model.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class TokenService {

    private static final String ISSUER = "API Palm Invest";

    private final SecretKey key;
    private final long expiracaoHoras;

    // A chave vem da variável de ambiente JWT_SECRET (Base64), nunca do código
    public TokenService(@Value("${api.security.token.secret}") String secret,
                        @Value("${api.security.token.expiracao-horas}") long expiracaoHoras) {
        byte[] bytes = Decoders.BASE64.decode(secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 256 bits (32 bytes) em Base64");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiracaoHoras = expiracaoHoras;
    }

    public String gerarToken(Usuario usuario) {
        var agora = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(usuario.getEmail())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoHoras, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    // Valida assinatura, emissor e expiração; lança JwtException se o token for inválido
    public String getSubject(String tokenJWT) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(tokenJWT)
                .getPayload()
                .getSubject();
    }
}
