package com.palminvest.api.dto;

import com.palminvest.api.infra.cotacao.ProvedorExterno;

import java.math.BigDecimal;
import java.time.Instant;

// Cotação atual de um símbolo em reais; updatedAt é o horário informado pelo provedor (a Brapi gratuita tem ~30 min de atraso)
public record DadosCotacaoDTO(String symbol, BigDecimal price, String currency, ProvedorExterno provider, Instant updatedAt) {
}
