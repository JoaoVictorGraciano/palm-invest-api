package com.palminvest.api.repository;

import com.palminvest.api.model.AssetClass;

// Projeção (símbolo, classe) das transações de um usuário, para escolher o provedor de cotação de cada símbolo
public record SimboloClasse(String assetSymbol, AssetClass assetClass) {
}
