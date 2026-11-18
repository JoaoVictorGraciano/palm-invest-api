package com.palminvest.api.dto;

import com.palminvest.api.model.AssetClass;

// Uma sugestão da busca de ativos: o front grava symbol e assetClass na transação
public record DadosSugestaoAtivoDTO(String symbol, String name, AssetClass assetClass) {
}
