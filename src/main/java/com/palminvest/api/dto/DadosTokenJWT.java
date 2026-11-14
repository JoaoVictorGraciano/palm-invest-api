package com.palminvest.api.dto;

// Resposta do login: { "token": "eyJh...", "expiresIn": 7200 } (validade em segundos, a partir de agora)
public record DadosTokenJWT(String token, long expiresIn) {
}
