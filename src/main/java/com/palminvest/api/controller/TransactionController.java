package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosCadastroTransacaoDTO;
import com.palminvest.api.dto.DadosDetalhamentoTransacaoDTO;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Toda rota usa o usuário do token (@AuthenticationPrincipal): ninguém enxerga transação de outra pessoa.
// As regras (saldo de venda) ficam no TransactionService; aqui só HTTP.
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionService service;

    // Rota GET: Retorna só as transações do usuário logado, ordenadas por data (mais recente primeiro)
    @GetMapping
    public List<DadosDetalhamentoTransacaoDTO> listarTodas(@AuthenticationPrincipal Usuario usuario) {
        return service.listar(usuario).stream().map(DadosDetalhamentoTransacaoDTO::new).toList();
    }

    // Rota POST: Recebe uma transação nova do Front e salva no banco com o usuário logado como dono
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DadosDetalhamentoTransacaoDTO criarTransacao(@RequestBody @Valid DadosCadastroTransacaoDTO dados,
                                                        @AuthenticationPrincipal Usuario usuario) {
        return new DadosDetalhamentoTransacaoDTO(service.criar(dados, usuario));
    }

    // Rota DELETE: Apaga uma transação do usuário logado (404 se não existir ou for de outro usuário)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTransacao(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return service.excluir(id, usuario)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // 🚀 ROTA PARA EDITAR A TRANSAÇÃO (404 se não existir ou for de outro usuário)
    @PutMapping("/{id}")
    public ResponseEntity<DadosDetalhamentoTransacaoDTO> updateTransaction(@PathVariable Long id,
                                                                           @RequestBody @Valid DadosCadastroTransacaoDTO dados,
                                                                           @AuthenticationPrincipal Usuario usuario) {
        return service.atualizar(id, dados, usuario)
                .map(transaction -> ResponseEntity.ok(new DadosDetalhamentoTransacaoDTO(transaction)))
                .orElse(ResponseEntity.notFound().build());
    }
}
