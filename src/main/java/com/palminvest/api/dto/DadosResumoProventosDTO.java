package com.palminvest.api.dto;

import com.palminvest.api.model.AssetClass;
import com.palminvest.api.model.TipoProvento;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta de GET /api/proventos/resumo?ano=: totais líquidos (bruto - IR) do ano.
 * porMes sempre tem os 12 meses e porTipo sempre tem os 4 tipos, com zero quando não houve provento;
 * porAtivo só traz os ativos que pagaram no ano, do maior total para o menor.
 */
public record DadosResumoProventosDTO(
        int ano,
        BigDecimal totalBruto,
        BigDecimal totalIrRetido,
        BigDecimal totalLiquido,
        List<TotalPorMes> porMes,
        List<TotalPorAtivo> porAtivo,
        List<TotalPorTipo> porTipo
) {
    public record TotalPorMes(int mes, BigDecimal totalLiquido) {
    }

    public record TotalPorAtivo(String assetSymbol, AssetClass assetClass, BigDecimal totalLiquido) {
    }

    public record TotalPorTipo(TipoProvento tipo, BigDecimal totalLiquido) {
    }
}
