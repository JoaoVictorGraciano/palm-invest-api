package com.palminvest.api.dto;

// Resposta do DELETE /api/transactions?assetSymbol=X: quantas transações e quantos proventos do ativo foram apagados
public record DadosExclusaoPorAtivoDTO(String assetSymbol, long excluidas, long proventosExcluidos) {
}
