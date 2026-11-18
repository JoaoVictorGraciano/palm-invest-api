package com.palminvest.api.model;

// Tipo do provento recebido; gravado como texto no banco (CHECK em V6)
public enum TipoProvento {
    DIVIDENDO,   // Dividendo de ação ou BDR
    JCP,         // Juros sobre capital próprio (normalmente com IR retido na fonte)
    RENDIMENTO,  // Rendimento de FII
    AMORTIZACAO  // Devolução de capital (ex: amortização de FII)
}
