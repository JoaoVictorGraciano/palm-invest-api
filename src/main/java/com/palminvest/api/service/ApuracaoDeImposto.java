package com.palminvest.api.service;

import com.palminvest.api.model.AssetClass;
import com.palminvest.api.service.RegrasDeImpostoDeRenda.GrupoDeCompensacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Apuração mensal estimada do imposto sobre as vendas, a partir do lucro realizado de cada venda (LinhaDoTempo).
 *
 * Para cada mês, em ordem, e para cada categoria (STOCK, ETF, BDR, FII, CRYPTO):
 * 1. soma o total vendido (quantidade × preço) e o lucro bruto (lucros − prejuízos das vendas do mês);
 * 2. se a categoria tem isenção e o total vendido não passou do limite, o mês é isento (lucro não tributado);
 * 3. os prejuízos do mês entram no saldo do grupo ANTES dos lucros, para compensar já no próprio mês;
 * 4. lucro tributável abate esse saldo, em ordem STOCK, ETF, BDR dentro do grupo;
 * 5. o que sobra é a base de cálculo, tributada pela alíquota da categoria.
 *
 * Compensação de prejuízo (escolhas desta versão):
 * - Grupos: STOCK, ETF e BDR juntos; FII sozinho. No mês, os resultados tributáveis de ETF e BDR somam com o de ações:
 *   prejuízo de ETF abate lucro de ações ou de BDR do mesmo mês, e vice-versa. O prejuízo que sobrar no mês vai para o
 *   saldo do grupo e abate lucro tributável dos meses seguintes. Como as três categorias têm a mesma alíquota, a ordem
 *   em que o saldo é usado dentro do mês não muda o imposto total, só em qual categoria aparece o abatimento.
 * - Ações isentas no mês (vendas até R$ 20 mil): o lucro delas não entra na soma e não absorve prejuízo de ETF ou BDR.
 *   Já o prejuízo de ações em mês isento entra no saldo (e abate lucro de ETF ou BDR do próprio mês).
 * - Cripto não compensa prejuízo: é ganho de capital, apurado venda a venda, e esta versão não acumula perdas de cripto.
 * - O saldo de prejuízo atravessa os anos (prejuízo de 2024 abate lucro de 2025).
 */
final class ApuracaoDeImposto {

    // Ordem de apresentação e de uso do saldo de prejuízo dentro do mês
    private static final List<AssetClass> ORDEM_CATEGORIAS =
            List.of(AssetClass.STOCK, AssetClass.ETF, AssetClass.BDR, AssetClass.FII, AssetClass.CRYPTO);

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private ApuracaoDeImposto() {
    }

    /**
     * @param vendas todas as vendas do usuário (todos os anos), com o lucro já calculado
     */
    static Apuracao apurar(List<LinhaDoTempo.Venda> vendas) {
        // mês -> categoria -> [total vendido, lucro bruto]
        var porMes = new TreeMap<YearMonth, Map<AssetClass, BigDecimal[]>>();
        for (var venda : vendas) {
            var mes = YearMonth.from(venda.transacao().getTransactionDate());
            var somas = porMes.computeIfAbsent(mes, m -> new EnumMap<>(AssetClass.class))
                    .computeIfAbsent(venda.transacao().getAssetClass(), c -> new BigDecimal[]{ZERO, ZERO});
            somas[0] = somas[0].add(venda.valorVenda());
            somas[1] = somas[1].add(venda.lucro());
        }

        var saldo = saldoZerado();
        var saldoAoFimDoMes = new TreeMap<YearMonth, Map<GrupoDeCompensacao, BigDecimal>>();
        var resultados = new ArrayList<ResultadoCategoria>();

        for (var entrada : porMes.entrySet()) {
            var mes = entrada.getKey();
            var vendasDoMes = entrada.getValue();
            var vencimento = RegrasDeImpostoDeRenda.vencimentoDarf(mes);

            // Prejuízos do mês primeiro: compensam lucros do próprio mês no grupo, e o que sobrar segue para os seguintes
            vendasDoMes.forEach((categoria, somas) -> {
                var grupo = RegrasDeImpostoDeRenda.de(categoria).grupo();
                if (somas[1].signum() < 0 && grupo != GrupoDeCompensacao.SEM_COMPENSACAO) {
                    saldo.merge(grupo, somas[1].negate(), BigDecimal::add);
                }
            });

            for (var categoria : ORDEM_CATEGORIAS) {
                var somas = vendasDoMes.get(categoria);
                if (somas == null) {
                    continue;
                }
                var regra = RegrasDeImpostoDeRenda.de(categoria);
                var totalVendas = somas[0];
                var lucroBruto = somas[1];
                var isento = regra.isento(totalVendas);
                var compensado = ZERO;
                var base = ZERO;

                // Lucro isento não entra na soma do grupo e não consome o saldo
                if (lucroBruto.signum() > 0 && !isento) {
                    if (regra.grupo() != GrupoDeCompensacao.SEM_COMPENSACAO) {
                        compensado = saldo.get(regra.grupo()).min(lucroBruto);
                        saldo.merge(regra.grupo(), compensado.negate(), BigDecimal::add);
                    }
                    base = lucroBruto.subtract(compensado);
                }

                var imposto = base.multiply(regra.aliquota()).setScale(2, RoundingMode.HALF_UP);
                resultados.add(new ResultadoCategoria(mes, categoria, totalVendas, lucroBruto, compensado, base, isento,
                        regra.aliquota(), imposto, regra.codigoDarf(), vencimento));
            }

            saldoAoFimDoMes.put(mes, new EnumMap<>(saldo));
        }
        return new Apuracao(resultados, saldoAoFimDoMes);
    }

    private static Map<GrupoDeCompensacao, BigDecimal> saldoZerado() {
        var saldo = new EnumMap<GrupoDeCompensacao, BigDecimal>(GrupoDeCompensacao.class);
        saldo.put(GrupoDeCompensacao.ACOES_ETF_BDR, ZERO);
        saldo.put(GrupoDeCompensacao.FII, ZERO);
        return saldo;
    }

    /**
     * @param lucroBruto         lucros − prejuízos das vendas da categoria no mês (negativo = prejuízo do mês)
     * @param prejuizoCompensado quanto de prejuízo do grupo (de outra categoria no mesmo mês ou de meses anteriores)
     *                           abateu este lucro
     * @param baseCalculo        lucro tributável depois da compensação (zero se isento ou com prejuízo)
     * @param imposto            estimativa: base × alíquota
     */
    record ResultadoCategoria(YearMonth mes, AssetClass categoria, BigDecimal totalVendas, BigDecimal lucroBruto,
                              BigDecimal prejuizoCompensado, BigDecimal baseCalculo, boolean isento,
                              BigDecimal aliquota, BigDecimal imposto, String codigoDarf, LocalDate vencimento) {
    }

    // saldoAoFimDoMes: prejuízo ainda a compensar por grupo, ao fim de cada mês com vendas
    record Apuracao(List<ResultadoCategoria> resultados, TreeMap<YearMonth, Map<GrupoDeCompensacao, BigDecimal>> saldoAoFimDoMes) {

        // Saldo de prejuízo a compensar ao fim do mês informado (zero antes da primeira venda)
        Map<GrupoDeCompensacao, BigDecimal> saldoEm(YearMonth mes) {
            var entrada = saldoAoFimDoMes.floorEntry(mes);
            return entrada == null ? saldoZerado() : entrada.getValue();
        }
    }
}
