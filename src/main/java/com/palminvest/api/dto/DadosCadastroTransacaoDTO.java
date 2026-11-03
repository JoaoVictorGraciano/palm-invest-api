package com.palminvest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Dados que o Front pode enviar no POST/PUT. Não tem "id": quem gera é o banco.
public record DadosCadastroTransacaoDTO(
        @NotBlank @Size(max = 20) String type,
        @NotBlank @Size(max = 20) String assetSymbol,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal price,
        @NotNull @PastOrPresent LocalDateTime transactionDate,
        @Size(max = 100) String broker,
        @PositiveOrZero BigDecimal fee,
        @Size(max = 255) String observation
) {
}
