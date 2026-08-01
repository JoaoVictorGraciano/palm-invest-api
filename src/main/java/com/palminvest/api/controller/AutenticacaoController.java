package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosAutenticacaoDTO;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    @PostMapping("/login")
    public ResponseEntity efetuarLogin(@RequestBody @Valid DadosAutenticacaoDTO dados) {
        // Pega o e-mail e senha recebidos
        var authenticationToken = new UsernamePasswordAuthenticationToken(dados.email(), dados.senha());
        // Vai no banco (através do AutenticacaoService) verificar se existe
        var authentication = manager.authenticate(authenticationToken);

        // Se passar, gera o Token JWT
        var tokenJWT = tokenService.gerarToken((Usuario) authentication.getPrincipal());

        // Devolve o Token para o Front-End no formato JSON: { "token": "eyjh..." }
        return ResponseEntity.ok(new DadosTokenJWT(tokenJWT));
    }
    
    // Um Record interno rapidinho só para formatar a resposta JSON do token
    private record DadosTokenJWT(String token) {}
}