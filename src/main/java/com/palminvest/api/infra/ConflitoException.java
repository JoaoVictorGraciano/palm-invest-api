package com.palminvest.api.infra;

// Lançada quando a requisição conflita com dados que já existem (ex: e-mail já cadastrado)
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
