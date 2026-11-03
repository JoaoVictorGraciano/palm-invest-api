package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosCadastroTransacaoDTO;
import com.palminvest.api.dto.DadosDetalhamentoTransacaoDTO;
import com.palminvest.api.model.Transaction;
import com.palminvest.api.repository.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*") // Magia pura: impede aquele erro chato de CORS quando o React tentar acessar
public class TransactionController {

    @Autowired
    private TransactionRepository repository;

    // Rota GET: Retorna todas as transações do banco
    @GetMapping
    public List<DadosDetalhamentoTransacaoDTO> listarTodas() {
        return repository.findAll().stream().map(DadosDetalhamentoTransacaoDTO::new).toList();
    }

    // Rota POST: Recebe uma transação nova do Front e salva no banco
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DadosDetalhamentoTransacaoDTO criarTransacao(@RequestBody @Valid DadosCadastroTransacaoDTO dados) {
        var transaction = repository.save(new Transaction(dados));
        return new DadosDetalhamentoTransacaoDTO(transaction);
    }

    // Rota DELETE: Apaga uma transação específica pelo ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTransacao(@PathVariable Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // 🚀 ROTA PARA EDITAR A TRANSAÇÃO
    @PutMapping("/{id}")
    public ResponseEntity<DadosDetalhamentoTransacaoDTO> updateTransaction(@PathVariable Long id, @RequestBody @Valid DadosCadastroTransacaoDTO dados) {
        return repository.findById(id)
                .map(transaction -> {
                    transaction.atualizarInformacoes(dados);

                    Transaction savedTransaction = repository.save(transaction);
                    return ResponseEntity.ok(new DadosDetalhamentoTransacaoDTO(savedTransaction));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
