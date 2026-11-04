package com.palminvest.api.dto;

// Resposta do login no formato JSON: { "token": "eyJh..." }
public record DadosTokenJWT(String token) {
}
