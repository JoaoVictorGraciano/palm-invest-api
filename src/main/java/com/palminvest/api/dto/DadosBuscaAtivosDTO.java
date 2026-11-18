package com.palminvest.api.dto;

import com.palminvest.api.infra.cotacao.ProvedorExterno;

import java.util.List;

// Resposta de GET /api/assets/search: sugestões já ordenadas e os provedores que falharam nesta busca
public record DadosBuscaAtivosDTO(List<DadosSugestaoAtivoDTO> suggestions, List<ProvedorExterno> failedProviders) {
}
