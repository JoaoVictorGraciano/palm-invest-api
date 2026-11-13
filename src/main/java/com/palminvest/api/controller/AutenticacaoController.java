package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosAutenticacaoDTO;
import com.palminvest.api.dto.DadosCadastroUsuarioDTO;
import com.palminvest.api.dto.DadosDetalhamentoUsuarioDTO;
import com.palminvest.api.dto.DadosTokenJWT;
import com.palminvest.api.infra.ConflitoException;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.repository.UsuarioRepository;
import com.palminvest.api.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AutenticacaoController {

    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<DadosTokenJWT> efetuarLogin(@RequestBody @Valid DadosAutenticacaoDTO dados) {
        // Pega o e-mail e senha recebidos
        var authenticationToken = new UsernamePasswordAuthenticationToken(dados.email(), dados.senha());
        // Vai no banco (através do AutenticacaoService) verificar se existe
        var authentication = manager.authenticate(authenticationToken);

        // Se passar, gera o Token JWT
        var tokenJWT = tokenService.gerarToken((Usuario) authentication.getPrincipal());

        // Devolve o Token para o Front-End no formato JSON: { "token": "eyjh..." }
        return ResponseEntity.ok(new DadosTokenJWT(tokenJWT));
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<DadosDetalhamentoUsuarioDTO> cadastrar(@RequestBody @Valid DadosCadastroUsuarioDTO dados) {
        if (repository.existsByEmail(dados.email())) {
            throw new ConflitoException("E-mail já cadastrado");
        }

        // Nunca salva a senha pura: guarda só o hash BCrypt
        var usuario = new Usuario(dados.nome(), dados.email(), passwordEncoder.encode(dados.senha()));
        repository.save(usuario);

        return ResponseEntity.status(HttpStatus.CREATED).body(new DadosDetalhamentoUsuarioDTO(usuario));
    }

    // Quem sou eu: dados do usuário dono do token (rota protegida; sem token o filtro já responde 401)
    @GetMapping("/me")
    public DadosDetalhamentoUsuarioDTO me(@AuthenticationPrincipal Usuario usuario) {
        return new DadosDetalhamentoUsuarioDTO(usuario);
    }
}
