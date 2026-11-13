package com.palminvest.api.infra;

// Lançada quando a requisição é válida na forma, mas viola uma regra do domínio (ex: vender mais do que se tem)
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
