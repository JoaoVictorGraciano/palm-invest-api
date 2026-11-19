package com.palminvest.api.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Resposta de GET /api/resultados/limite-isencao?mes=AAAA-MM: quanto já foi vendido no mês em ações e em cripto e
 * quanto ainda dá para vender sem perder a isenção (R$ 20.000 em ações, R$ 35.000 somando todas as criptos).
 */
public record DadosLimiteIsencaoDTO(YearMonth mes, Limite acoes, Limite cripto) {

    // disponivel nunca fica negativo; dentroDoLimite = vendido <= limite (o próprio limite ainda é isento)
    public record Limite(BigDecimal limite, BigDecimal vendido, BigDecimal disponivel, boolean dentroDoLimite) {
    }
}
