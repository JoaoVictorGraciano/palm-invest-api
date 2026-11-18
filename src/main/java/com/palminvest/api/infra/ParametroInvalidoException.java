package com.palminvest.api.infra;

// Parâmetro ou campo presente mas fora de uma regra que o Bean Validation não expressa (ex: busca com 1 caractere,
// IR maior que o valor bruto): 400 no mesmo formato da validação do corpo
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
