package com.palminvest.api.dto;

import com.palminvest.api.infra.cotacao.ProvedorExterno;

// Símbolo sem cotação nesta resposta e o motivo (não encontrado ou provedor indisponível); o front usa o custo médio
public record DadosFalhaCotacaoDTO(String symbol, ProvedorExterno provider, String mensagem) {
}
