package com.palminvest.api.infra.cotacao;

import java.util.List;

// Todos os provedores consultados falharam e não há nada para devolver: o TratadorDeErros responde 503
public class ProvedoresIndisponiveisException extends RuntimeException {

    public ProvedoresIndisponiveisException(List<ProvedorIndisponivelException> falhas) {
        super("Não foi possível consultar os provedores de mercado agora: "
                + String.join("; ", falhas.stream().map(Throwable::getMessage).distinct().toList()));
    }
}
