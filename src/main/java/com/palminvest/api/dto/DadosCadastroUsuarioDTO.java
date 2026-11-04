package com.palminvest.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

// A senha vai até 72 caracteres porque o BCrypt ignora o que passa disso
public record DadosCadastroUsuarioDTO(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String senha
) {
    // E-mail sempre em minúsculas e sem espaços, para "Joao@X.com " e "joao@x.com" serem o mesmo usuário
    public DadosCadastroUsuarioDTO {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
