package com.palminvest.api.model;

import java.util.Optional;

// Filtro de classe da busca de ativos (?class=): ALL consulta todos os provedores; as demais, só o provedor da classe
public enum AssetClassFilter {
    ALL(null),
    STOCK(AssetClass.STOCK),
    FII(AssetClass.FII),
    ETF(AssetClass.ETF),
    BDR(AssetClass.BDR),
    CRYPTO(AssetClass.CRYPTO);

    private final AssetClass classe;

    AssetClassFilter(AssetClass classe) {
        this.classe = classe;
    }

    public Optional<AssetClass> classe() {
        return Optional.ofNullable(classe);
    }
}
