package com.palminvest.api.model;

// Gravados como texto no banco (CHECK em V5). STOCK e CRYPTO são os valores que o front já usa (ASSET_CLASS);
// FII, ETF e BDR são aceitos pela API e o front precisa acompanhar para exibi-los e cotá-los.
public enum AssetClass {
    STOCK,  // Ação da B3
    CRYPTO, // Criptomoeda
    FII,    // Fundo imobiliário
    ETF,    // Fundo de índice
    BDR     // Recibo de ação estrangeira negociado na B3
}
