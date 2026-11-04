package com.palminvest.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

public record DadosAutenticacaoDTO(
        @NotBlank @Email String email,
        @NotBlank String senha
) {
    // Mesma normalização do cadastro, para o login encontrar o usuário
    public DadosAutenticacaoDTO {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
