package com.palminvest.api.dto;

import java.util.List;

// Resposta de GET /api/quotes: cotações obtidas e falhas, ambas na ordem dos símbolos pedidos
public record DadosCotacoesDTO(List<DadosCotacaoDTO> quotes, List<DadosFalhaCotacaoDTO> failures) {
}
