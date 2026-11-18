package com.palminvest.api.dto;

import com.palminvest.api.model.TipoProvento;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

// Dados que o Front envia no POST/PUT de proventos. Sem "id" (gerado pelo banco) e sem assetClass,
// que é copiada da transação do usuário daquele ativo. Os @Digits espelham as colunas numeric(19,2).
public record DadosCadastroProventoDTO(
        @NotBlank @Size(max = 20) String assetSymbol,
        @NotNull TipoProvento tipo,
        @NotNull @PastOrPresent LocalDate dataPagamento,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal valorBruto,
        @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal irRetido,
        @Size(max = 255) String observacao
) {
    public DadosCadastroProventoDTO {
        // Mesmo símbolo das transações: maiúsculas e sem espaços
        if (assetSymbol != null) {
            assetSymbol = assetSymbol.trim().toUpperCase(Locale.ROOT);
        }
        // Sem IR informado é zero (dividendos e rendimentos de FII costumam ser isentos)
        if (irRetido == null) {
            irRetido = BigDecimal.ZERO;
        }
    }
}
