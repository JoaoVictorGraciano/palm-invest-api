package com.palminvest.api.dto;

import com.palminvest.api.model.AssetClass;
import com.palminvest.api.model.Provento;
import com.palminvest.api.model.TipoProvento;

import java.math.BigDecimal;
import java.time.LocalDate;

// Provento devolvido para o Front, com o valor líquido (bruto - IR) já calculado
public record DadosDetalhamentoProventoDTO(
        Long id,
        String assetSymbol,
        AssetClass assetClass,
        TipoProvento tipo,
        LocalDate dataPagamento,
        BigDecimal valorBruto,
        BigDecimal irRetido,
        BigDecimal valorLiquido,
        String observacao
) {
    public DadosDetalhamentoProventoDTO(Provento provento) {
        this(provento.getId(), provento.getAssetSymbol(), provento.getAssetClass(), provento.getTipo(),
                provento.getDataPagamento(), provento.getValorBruto(), provento.getIrRetido(),
                provento.getValorLiquido(), provento.getObservacao());
    }
}
