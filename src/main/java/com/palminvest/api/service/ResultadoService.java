package com.palminvest.api.service;

import com.palminvest.api.dto.DadosLimiteIsencaoDTO;
import com.palminvest.api.dto.DadosResultadosAnoDTO;
import com.palminvest.api.model.AssetClass;
import com.palminvest.api.model.Transaction;
import com.palminvest.api.model.TransactionType;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.repository.TransactionRepository;
import com.palminvest.api.service.RegrasDeImpostoDeRenda.GrupoDeCompensacao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Lucro realizado e ESTIMATIVA de imposto de renda sobre vendas, calculados a partir das transações do usuário
 * (sem tabela própria): LinhaDoTempo dá o lucro de cada venda, ApuracaoDeImposto aplica isenção e compensação,
 * RegrasDeImpostoDeRenda tem as alíquotas e limites. Não substitui um contador.
 */
@Service
public class ResultadoService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");

    @Autowired
    private TransactionRepository transactions;

    @Transactional(readOnly = true)
    public DadosResultadosAnoDTO doAno(Usuario usuario, int ano) {
        var transacoes = transactions.findAllByUsuarioOrderByTransactionDateDescIdDesc(usuario);
        // Apura todo o histórico: o saldo de prejuízo de anos anteriores vale para este ano
        var apuracao = ApuracaoDeImposto.apurar(LinhaDoTempo.vendas(transacoes));
        var doAno = apuracao.resultados().stream().filter(r -> r.mes().getYear() == ano).toList();
        var porMes = doAno.stream().collect(Collectors.groupingBy(ApuracaoDeImposto.ResultadoCategoria::mes, TreeMap::new, Collectors.toList()));

        var meses = new ArrayList<DadosResultadosAnoDTO.Mes>();
        var alertas = new ArrayList<DadosResultadosAnoDTO.Alerta>();
        for (int numero = 1; numero <= 12; numero++) {
            var mes = YearMonth.of(ano, numero);
            var resultados = porMes.getOrDefault(mes, List.of());
            var darfs = darfsDoMes(resultados);
            darfs.stream()
                    .filter(darf -> darf.impostoEstimado().compareTo(RegrasDeImpostoDeRenda.DARF_MINIMO) < 0)
                    .forEach(darf -> alertas.add(new DadosResultadosAnoDTO.Alerta("DARF_ABAIXO_DO_MINIMO", mes, null, null,
                            "DARF %s de %s estimado em %s: abaixo de R$ 10,00 não se recolhe; o valor se soma ao imposto do mês seguinte"
                                    .formatted(darf.codigoDarf(), MES.format(mes), reais(darf.impostoEstimado())))));
            meses.add(new DadosResultadosAnoDTO.Mes(mes,
                    resultados.stream().map(ResultadoService::categoria).toList(),
                    darfs,
                    soma(resultados, ApuracaoDeImposto.ResultadoCategoria::imposto)));
        }

        alertas.addAll(0, alertasDeDayTrade(transacoes, ano));
        return new DadosResultadosAnoDTO(ano, meses,
                new DadosResultadosAnoDTO.TotalAno(
                        soma(doAno, ApuracaoDeImposto.ResultadoCategoria::totalVendas),
                        soma(doAno, ApuracaoDeImposto.ResultadoCategoria::lucroBruto),
                        soma(doAno, ApuracaoDeImposto.ResultadoCategoria::prejuizoCompensado),
                        soma(doAno, ApuracaoDeImposto.ResultadoCategoria::baseCalculo),
                        soma(doAno, ApuracaoDeImposto.ResultadoCategoria::imposto)),
                new DadosResultadosAnoDTO.PrejuizoAcumulado(
                        porGrupo(apuracao.saldoEm(YearMonth.of(ano - 1, 12))),
                        porGrupo(apuracao.saldoEm(YearMonth.of(ano, 12)))),
                alertas);
    }

    // Quanto já foi vendido no mês em ações e em cripto e quanto ainda cabe no limite de isenção de cada um
    @Transactional(readOnly = true)
    public DadosLimiteIsencaoDTO limiteDeIsencao(Usuario usuario, YearMonth mes) {
        var vendidoPorClasse = new EnumMap<AssetClass, BigDecimal>(AssetClass.class);
        for (var t : transactions.findAllByUsuarioOrderByTransactionDateDescIdDesc(usuario)) {
            if (t.getType() == TransactionType.SELL && YearMonth.from(t.getTransactionDate()).equals(mes)) {
                // Mesmo valor de venda da apuração: quantidade × preço, sem descontar a taxa
                vendidoPorClasse.merge(t.getAssetClass(), t.getQuantity().multiply(t.getPrice()), BigDecimal::add);
            }
        }
        return new DadosLimiteIsencaoDTO(mes,
                limite(RegrasDeImpostoDeRenda.LIMITE_ISENCAO_ACOES, vendidoPorClasse.getOrDefault(AssetClass.STOCK, ZERO)),
                limite(RegrasDeImpostoDeRenda.LIMITE_ISENCAO_CRIPTO, vendidoPorClasse.getOrDefault(AssetClass.CRYPTO, ZERO)));
    }

    private static DadosLimiteIsencaoDTO.Limite limite(BigDecimal limite, BigDecimal vendido) {
        var vendidoEmReais = vendido.setScale(2, RoundingMode.HALF_UP);
        var disponivel = limite.subtract(vendidoEmReais).max(ZERO);
        return new DadosLimiteIsencaoDTO.Limite(limite, vendidoEmReais, disponivel, vendidoEmReais.compareTo(limite) <= 0);
    }

    // Um DARF por código no mês (6015 junta ações, ETF, BDR e FII), só quando há imposto
    private static List<DadosResultadosAnoDTO.Darf> darfsDoMes(List<ApuracaoDeImposto.ResultadoCategoria> resultados) {
        var porCodigo = new TreeMap<String, BigDecimal>();
        LocalDate vencimento = null;
        for (var r : resultados) {
            if (r.imposto().signum() > 0) {
                porCodigo.merge(r.codigoDarf(), r.imposto(), BigDecimal::add);
                vencimento = r.vencimento();
            }
        }
        var vence = vencimento;
        return porCodigo.entrySet().stream()
                .map(e -> new DadosResultadosAnoDTO.Darf(e.getKey(), e.getValue(), vence))
                .toList();
    }

    /**
     * Compra e venda do mesmo ativo no mesmo dia: possível day trade, que tem alíquota de 20% e DARF próprio.
     * A estimativa trata tudo como operação comum; o alerta só avisa para conferir.
     */
    private static List<DadosResultadosAnoDTO.Alerta> alertasDeDayTrade(List<Transaction> transacoes, int ano) {
        record AtivoNoDia(String simbolo, LocalDate data) {
        }
        var tiposPorDia = transacoes.stream()
                .filter(t -> t.getTransactionDate().getYear() == ano)
                .collect(Collectors.groupingBy(t -> new AtivoNoDia(t.getAssetSymbol(), t.getTransactionDate().toLocalDate()),
                        Collectors.mapping(Transaction::getType, Collectors.toSet())));
        return tiposPorDia.entrySet().stream()
                .filter(e -> e.getValue().contains(TransactionType.BUY) && e.getValue().contains(TransactionType.SELL))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(AtivoNoDia::data).thenComparing(AtivoNoDia::simbolo))
                .map(dia -> new DadosResultadosAnoDTO.Alerta("POSSIVEL_DAY_TRADE", YearMonth.from(dia.data()), dia.simbolo(), dia.data(),
                        "Compra e venda de %s em %s: possível day trade (alíquota de 20%% e DARF próprio). A estimativa não calcula day trade; confira com um contador."
                                .formatted(dia.simbolo(), DATA.format(dia.data()))))
                .toList();
    }

    private static DadosResultadosAnoDTO.Categoria categoria(ApuracaoDeImposto.ResultadoCategoria r) {
        return new DadosResultadosAnoDTO.Categoria(r.categoria(), r.totalVendas(), r.lucroBruto(), r.prejuizoCompensado(),
                r.baseCalculo(), r.isento(), r.aliquota(), r.imposto(), r.codigoDarf(), r.vencimento());
    }

    private static DadosResultadosAnoDTO.PrejuizoPorGrupo porGrupo(Map<GrupoDeCompensacao, BigDecimal> saldo) {
        return new DadosResultadosAnoDTO.PrejuizoPorGrupo(saldo.get(GrupoDeCompensacao.ACOES_ETF_BDR), saldo.get(GrupoDeCompensacao.FII));
    }

    private static BigDecimal soma(List<ApuracaoDeImposto.ResultadoCategoria> resultados,
                                   java.util.function.Function<ApuracaoDeImposto.ResultadoCategoria, BigDecimal> campo) {
        return resultados.stream().map(campo).reduce(ZERO, BigDecimal::add);
    }

    private static String reais(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor);
    }
}
