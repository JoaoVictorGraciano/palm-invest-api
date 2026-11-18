package com.palminvest.api.infra.cotacao;

import com.fasterxml.jackson.annotation.JsonValue;

// Provedores de mercado consultados pela API; no JSON aparecem em minúsculas ("brapi", "coingecko")
public enum ProvedorExterno {
    BRAPI("brapi", "Brapi"),
    COINGECKO("coingecko", "CoinGecko");

    private final String id;
    private final String nome;

    ProvedorExterno(String id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String nome() {
        return nome;
    }
}
