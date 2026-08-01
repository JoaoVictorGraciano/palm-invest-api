package com.palminvest.api.security;

import com.palminvest.api.model.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Service
public class TokenService {

    // Essa chave deveria vir do application.properties, mas para facilitar agora, vamos gerar uma em memória.
    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    public String gerarToken(Usuario usuario) {
        return Jwts.builder()
                .setIssuer("API Palm Invest")
                .setSubject(usuario.getEmail())
                .setExpiration(Date.from(dataExpiracao()))
                .signWith(key)
                .compact();
    }

    private Instant dataExpiracao() {
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }
}