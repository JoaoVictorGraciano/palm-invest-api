package com.palminvest.api.service;

import com.palminvest.api.model.AssetClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

/**
 * Regras de imposto de renda sobre ganhos na venda de ativos por pessoa física, usadas na ESTIMATIVA de
 * /api/resultados. Os valores ficam todos aqui para serem fáceis de revisar e mudar. Não substituem um contador.
 *
 * Base legal (consulte o texto vigente; as regras mudam):
 * - Ações (STOCK): 15% sobre o ganho líquido em operações comuns no mercado à vista; isenção quando o total de
 *   vendas de ações no mês é de até R$ 20.000 (Lei 11.033/2004, art. 3º, I). Passou do limite, o ganho do mês inteiro
 *   é tributado.
 * - ETF de renda variável: 15%, sem a isenção dos R$ 20.000 (Lei 13.043/2014; IN RFB 1.585/2015).
 * - BDR: 15%, sem a isenção dos R$ 20.000 (IN RFB 1.585/2015).
 * - FII: 20% sobre o ganho na venda de cotas, sem isenção (Lei 8.668/1993, art. 18, com redação da Lei 9.779/1999).
 * - Cripto: ganho de capital a 15% (Lei 8.981/1995, art. 21, com redação da Lei 13.259/2016), isento quando o total de
 *   vendas no mês, somando todas as criptos, é de até R$ 35.000 (Lei 9.250/1995, art. 22, II).
 *   As faixas acima de R$ 5 milhões de ganho (17,5% a 22,5%) não são consideradas nesta versão.
 * - Compensação de prejuízo (IN RFB 1.585/2015): ações, ETF e BDR compensam entre si; FII só com FII. Cripto é ganho de
 *   capital e não tem compensação de prejuízo nesta versão (ver ResultadoService).
 * - DARF: código 6015 para renda variável (ações, ETF, BDR, FII) e 4600 para ganho de capital na alienação de bens e
 *   direitos (cripto), com vencimento no último dia útil do mês seguinte ao da venda. DARF abaixo de R$ 10,00 não é
 *   recolhido; o valor se acumula para o mês seguinte (Lei 9.430/1996, art. 68).
 * - Day trade (15% → 20%, outro código) não é calculado: só há um alerta quando compra e venda caem no mesmo dia.
 */
public final class RegrasDeImpostoDeRenda {

    public static final BigDecimal LIMITE_ISENCAO_ACOES = new BigDecimal("20000.00");
    public static final BigDecimal LIMITE_ISENCAO_CRIPTO = new BigDecimal("35000.00");

    public static final BigDecimal ALIQUOTA_RENDA_VARIAVEL = new BigDecimal("0.15");
    public static final BigDecimal ALIQUOTA_FII = new BigDecimal("0.20");
    public static final BigDecimal ALIQUOTA_CRIPTO = new BigDecimal("0.15");

    public static final String DARF_RENDA_VARIAVEL = "6015";
    public static final String DARF_GANHO_DE_CAPITAL = "4600";

    // Abaixo disso não se recolhe DARF (Lei 9.430/1996, art. 68): só gera alerta
    public static final BigDecimal DARF_MINIMO = new BigDecimal("10.00");

    // Quem compensa prejuízo com quem
    public enum GrupoDeCompensacao {
        ACOES_ETF_BDR,
        FII,
        SEM_COMPENSACAO
    }

    /**
     * @param limiteIsencao total de vendas no mês até o qual o ganho é isento; null = sem isenção
     */
    public record Regra(AssetClass classe, BigDecimal aliquota, BigDecimal limiteIsencao, GrupoDeCompensacao grupo,
                        String codigoDarf) {

        // Isento se a categoria tem limite e o total vendido no mês não passou dele ("até R$ X" inclui o próprio X)
        public boolean isento(BigDecimal totalVendasNoMes) {
            return limiteIsencao != null && totalVendasNoMes.compareTo(limiteIsencao) <= 0;
        }
    }

    private static final Map<AssetClass, Regra> REGRAS = new EnumMap<>(Map.of(
            AssetClass.STOCK, new Regra(AssetClass.STOCK, ALIQUOTA_RENDA_VARIAVEL, LIMITE_ISENCAO_ACOES, GrupoDeCompensacao.ACOES_ETF_BDR, DARF_RENDA_VARIAVEL),
            AssetClass.ETF, new Regra(AssetClass.ETF, ALIQUOTA_RENDA_VARIAVEL, null, GrupoDeCompensacao.ACOES_ETF_BDR, DARF_RENDA_VARIAVEL),
            AssetClass.BDR, new Regra(AssetClass.BDR, ALIQUOTA_RENDA_VARIAVEL, null, GrupoDeCompensacao.ACOES_ETF_BDR, DARF_RENDA_VARIAVEL),
            AssetClass.FII, new Regra(AssetClass.FII, ALIQUOTA_FII, null, GrupoDeCompensacao.FII, DARF_RENDA_VARIAVEL),
            AssetClass.CRYPTO, new Regra(AssetClass.CRYPTO, ALIQUOTA_CRIPTO, LIMITE_ISENCAO_CRIPTO, GrupoDeCompensacao.SEM_COMPENSACAO, DARF_GANHO_DE_CAPITAL)
    ));

    private RegrasDeImpostoDeRenda() {
    }

    public static Regra de(AssetClass classe) {
        return REGRAS.get(classe);
    }

    // Vencimento do DARF das vendas feitas em mesDaVenda: último dia útil do mês seguinte
    public static LocalDate vencimentoDarf(YearMonth mesDaVenda) {
        return CalendarioBancario.ultimoDiaUtil(mesDaVenda.plusMonths(1));
    }
}
