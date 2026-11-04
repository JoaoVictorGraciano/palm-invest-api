package com.palminvest.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Dados que o Front pode enviar no POST/PUT. Não tem "id": quem gera é o banco.
// Os @Digits espelham as colunas (quantity numeric(19,8); price e fee numeric(38,2)):
// casas decimais a mais viram 400 em vez de serem arredondadas em silêncio pelo banco.
public record DadosCadastroTransacaoDTO(
        @NotBlank @Size(max = 20) String type,
        @NotBlank @Size(max = 20) String assetSymbol,
        @NotNull @Positive @Digits(integer = 11, fraction = 8) BigDecimal quantity,
        @NotNull @Positive @Digits(integer = 36, fraction = 2) BigDecimal price,
        @NotNull @PastOrPresent LocalDateTime transactionDate,
        @Size(max = 100) String broker,
        @PositiveOrZero @Digits(integer = 36, fraction = 2) BigDecimal fee,
        @Size(max = 255) String observation
) {
}
