package com.palminvest.api.service;

import com.palminvest.api.model.Transaction;
import com.palminvest.api.model.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Linha do tempo das transações de um ativo: a mesma ordem usada para validar vendas (TransactionService)
 * e para calcular o lucro realizado de cada venda pelo custo médio.
 */
final class LinhaDoTempo {

    // Mesma data: compra antes da venda (permite comprar e vender no mesmo instante); id desempata o resto
    static final Comparator<Transaction> ORDEM = Comparator.comparing(Transaction::getTransactionDate)
            .thenComparing(t -> t.getType() == TransactionType.SELL ? 1 : 0)
            .thenComparing(Transaction::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    // Casas do custo em memória (o lucro sai com 2); evita perder centavos em vendas parciais sucessivas
    private static final int ESCALA_CUSTO = 10;

    private LinhaDoTempo() {
    }

    static List<Transaction> ordenar(Collection<Transaction> transacoes) {
        var ordenadas = new ArrayList<>(transacoes);
        ordenadas.sort(ORDEM);
        return ordenadas;
    }

    /**
     * Lucro realizado de cada venda, pelo custo médio do ativo no momento da venda:
     * lucro = (quantidade × preço − taxa da venda) − quantidade × custo médio,
     * com o custo médio incluindo as taxas das compras: Σ(quantidade × preço + taxa) / Σ quantidade.
     * A venda não muda o custo médio do que sobra; zerada a posição, o custo recomeça na próxima compra.
     * O custo médio é por ativo, somando todas as corretoras.
     *
     * @param transacoes transações de um usuário (de um ou vários ativos, em qualquer ordem)
     * @return vendas na ordem da linha do tempo de cada ativo
     */
    static List<Venda> vendas(Collection<Transaction> transacoes) {
        var porAtivo = transacoes.stream().collect(Collectors.groupingBy(Transaction::getAssetSymbol));
        var vendas = new ArrayList<Venda>();
        for (var historico : porAtivo.values()) {
            var quantidade = BigDecimal.ZERO;
            var custoTotal = BigDecimal.ZERO;
            for (var t : ordenar(historico)) {
                var taxa = t.getFee() == null ? BigDecimal.ZERO : t.getFee();
                var valor = t.getQuantity().multiply(t.getPrice());
                if (t.getType() == TransactionType.BUY) {
                    quantidade = quantidade.add(t.getQuantity());
                    custoTotal = custoTotal.add(valor).add(taxa);
                    continue;
                }
                // Custo da parte vendida, proporcional ao que havia em carteira (= quantidade × custo médio)
                var custoVendido = quantidade.signum() <= 0 ? BigDecimal.ZERO
                        : custoTotal.multiply(t.getQuantity()).divide(quantidade, ESCALA_CUSTO, RoundingMode.HALF_UP);
                var lucro = valor.subtract(taxa).subtract(custoVendido).setScale(2, RoundingMode.HALF_UP);
                vendas.add(new Venda(t, valor.setScale(2, RoundingMode.HALF_UP), lucro));

                quantidade = quantidade.subtract(t.getQuantity());
                custoTotal = quantidade.signum() <= 0 ? BigDecimal.ZERO : custoTotal.subtract(custoVendido);
                if (quantidade.signum() < 0) {
                    quantidade = BigDecimal.ZERO; // só em histórico antigo inconsistente; a API não deixa vender sem saldo
                }
            }
        }
        return vendas;
    }

    // Lucro realizado por id de transação de venda
    static Map<Long, BigDecimal> lucroPorVenda(Collection<Transaction> transacoes) {
        var lucros = new HashMap<Long, BigDecimal>();
        for (var venda : vendas(transacoes)) {
            lucros.put(venda.transacao().getId(), venda.lucro());
        }
        return lucros;
    }

    /**
     * @param valorVenda quantidade × preço, sem descontar a taxa (é o que conta para o limite de isenção)
     * @param lucro      positivo é lucro, negativo é prejuízo, já com a taxa da venda descontada
     */
    record Venda(Transaction transacao, BigDecimal valorVenda, BigDecimal lucro) {
    }
}
