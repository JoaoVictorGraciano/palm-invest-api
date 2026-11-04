package com.palminvest.api.dto;

import com.palminvest.api.model.Usuario;

// Resposta do cadastro: nunca devolve a senha
public record DadosDetalhamentoUsuarioDTO(Long id, String nome, String email) {

    public DadosDetalhamentoUsuarioDTO(Usuario usuario) {
        this(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
