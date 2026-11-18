package com.palminvest.api.infra.cotacao;

// O provedor não respondeu, recusou a chamada (limite, token, plano) ou devolveu algo inesperado.
// A mensagem é segura para o usuário: nunca contém URL com parâmetros, token ou chave.
public class ProvedorIndisponivelException extends RuntimeException {

    private final ProvedorExterno provedor;

    public ProvedorIndisponivelException(ProvedorExterno provedor, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.provedor = provedor;
    }

    public ProvedorExterno provedor() {
        return provedor;
    }
}
