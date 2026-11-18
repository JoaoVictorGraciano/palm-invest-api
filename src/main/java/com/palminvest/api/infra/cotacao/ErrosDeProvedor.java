package com.palminvest.api.infra.cotacao;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// Traduz falhas do RestClient em mensagens curtas para o usuário, sem repassar URL, corpo ou headers
final class ErrosDeProvedor {

    private ErrosDeProvedor() {
    }

    static ProvedorIndisponivelException traduzir(ProvedorExterno provedor, RestClientException erro) {
        var nome = provedor.nome();
        String mensagem;
        if (erro instanceof RestClientResponseException resposta) {
            int status = resposta.getStatusCode().value();
            mensagem = switch (status) {
                case 429 -> nome + " recusou a consulta: limite de requisições atingido";
                case 401, 402, 403 -> nome + " recusou a consulta: token ausente, inválido ou sem acesso a este ativo no plano";
                default -> nome + " respondeu com erro (HTTP " + status + ")";
            };
        } else if (erro instanceof ResourceAccessException) {
            mensagem = nome + " não respondeu a tempo";
        } else {
            mensagem = nome + " devolveu uma resposta inesperada";
        }
        return new ProvedorIndisponivelException(provedor, mensagem, erro);
    }
}
