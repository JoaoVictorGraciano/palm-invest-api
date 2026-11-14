package com.palminvest.api.dto;

import com.palminvest.api.model.AssetClass;
import com.palminvest.api.model.Transaction;
import com.palminvest.api.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Formato da transação devolvida para o Front (nunca expõe a entidade direto)
public record DadosDetalhamentoTransacaoDTO(
        Long id,
        TransactionType type,
        String assetSymbol,
        AssetClass assetClass,
        BigDecimal quantity,
        BigDecimal price,
        LocalDateTime transactionDate,
        String broker,
        BigDecimal fee,
        String observation
) {
    public DadosDetalhamentoTransacaoDTO(Transaction transaction) {
        this(transaction.getId(), transaction.getType(), transaction.getAssetSymbol(), transaction.getAssetClass(),
                transaction.getQuantity(), transaction.getPrice(), transaction.getTransactionDate(),
                transaction.getBroker(), transaction.getFee(), transaction.getObservation());
    }
}
