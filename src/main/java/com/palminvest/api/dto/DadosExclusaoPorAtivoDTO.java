package com.palminvest.api.dto;

// Resposta do DELETE /api/transactions?assetSymbol=X: quantas transações do ativo foram apagadas
public record DadosExclusaoPorAtivoDTO(String assetSymbol, long excluidas) {
}
