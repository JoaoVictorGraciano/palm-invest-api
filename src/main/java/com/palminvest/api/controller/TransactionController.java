package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosCadastroTransacaoDTO;
import com.palminvest.api.dto.DadosDetalhamentoTransacaoDTO;
import com.palminvest.api.model.Transaction;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.repository.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Toda rota usa o usuário do token (@AuthenticationPrincipal): ninguém enxerga transação de outra pessoa
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionRepository repository;

    // Rota GET: Retorna só as transações do usuário logado, ordenadas por data (mais recente primeiro)
    @GetMapping
    public List<DadosDetalhamentoTransacaoDTO> listarTodas(@AuthenticationPrincipal Usuario usuario) {
        return repository.findAllByUsuarioOrderByTransactionDateDescIdDesc(usuario).stream().map(DadosDetalhamentoTransacaoDTO::new).toList();
    }

    // Rota POST: Recebe uma transação nova do Front e salva no banco com o usuário logado como dono
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DadosDetalhamentoTransacaoDTO criarTransacao(@RequestBody @Valid DadosCadastroTransacaoDTO dados,
                                                        @AuthenticationPrincipal Usuario usuario) {
        var transaction = repository.save(new Transaction(dados, usuario));
        return new DadosDetalhamentoTransacaoDTO(transaction);
    }

    // Rota DELETE: Apaga uma transação do usuário logado (404 se não existir ou for de outro usuário)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTransacao(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return repository.findByIdAndUsuario(id, usuario)
                .map(transaction -> {
                    repository.delete(transaction);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 🚀 ROTA PARA EDITAR A TRANSAÇÃO (404 se não existir ou for de outro usuário)
    @PutMapping("/{id}")
    public ResponseEntity<DadosDetalhamentoTransacaoDTO> updateTransaction(@PathVariable Long id,
                                                                           @RequestBody @Valid DadosCadastroTransacaoDTO dados,
                                                                           @AuthenticationPrincipal Usuario usuario) {
        return repository.findByIdAndUsuario(id, usuario)
                .map(transaction -> {
                    transaction.atualizarInformacoes(dados);

                    Transaction savedTransaction = repository.save(transaction);
                    return ResponseEntity.ok(new DadosDetalhamentoTransacaoDTO(savedTransaction));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
