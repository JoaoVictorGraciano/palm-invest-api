package com.palminvest.api.infra;

// Query param presente mas inválido (ex: busca com 1 caractere): 400 no mesmo formato da validação do corpo
public class ParametroInvalidoException extends RuntimeException {

    private final String campo;

    public ParametroInvalidoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
