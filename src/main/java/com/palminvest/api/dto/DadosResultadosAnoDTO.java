package com.palminvest.api.dto;

import com.palminvest.api.model.AssetClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Resposta de GET /api/resultados?ano=: ESTIMATIVA de lucro realizado e imposto de renda sobre vendas, mês a mês.
 * Não substitui um contador. meses sempre tem os 12 meses do ano; cada mês só lista as categorias com vendas.
 */
public record DadosResultadosAnoDTO(
        int ano,
        List<Mes> meses,
        TotalAno totalAno,
        PrejuizoAcumulado prejuizoAcumulado,
        List<Alerta> alertas
) {
    // darfs: um por código com imposto no mês (6015 soma ações, ETF, BDR e FII; 4600 é cripto)
    public record Mes(YearMonth mes, List<Categoria> categorias, List<Darf> darfs, BigDecimal impostoEstimado) {
    }

    /**
     * @param lucroBruto         lucros − prejuízos das vendas do mês (negativo = prejuízo)
     * @param prejuizoCompensado prejuízo do mesmo grupo (de outra categoria no mesmo mês ou de meses anteriores) usado
     *                           para abater este lucro
     * @param baseCalculo        lucro tributável depois da compensação (zero se isento ou com prejuízo)
     * @param isento             vendas do mês dentro do limite de isenção da categoria (só STOCK e CRYPTO têm limite)
     */
    public record Categoria(AssetClass categoria, BigDecimal totalVendas, BigDecimal lucroBruto,
                            BigDecimal prejuizoCompensado, BigDecimal baseCalculo, boolean isento, BigDecimal aliquota,
                            BigDecimal impostoEstimado, String codigoDarf, LocalDate vencimentoDarf) {
    }

    public record Darf(String codigoDarf, BigDecimal impostoEstimado, LocalDate vencimento) {
    }

    public record TotalAno(BigDecimal totalVendas, BigDecimal lucroBruto, BigDecimal prejuizoCompensado,
                           BigDecimal baseCalculo, BigDecimal impostoEstimado) {
    }

    // Saldo de prejuízo ainda a compensar, por grupo, antes do ano começar e ao fim do ano
    public record PrejuizoAcumulado(PrejuizoPorGrupo inicioDoAno, PrejuizoPorGrupo fimDoAno) {
    }

    public record PrejuizoPorGrupo(BigDecimal acoesEtfBdr, BigDecimal fii) {
    }

    // tipo: POSSIVEL_DAY_TRADE ou DARF_ABAIXO_DO_MINIMO; assetSymbol e data só no day trade
    public record Alerta(String tipo, YearMonth mes, String assetSymbol, LocalDate data, String mensagem) {
    }
}
