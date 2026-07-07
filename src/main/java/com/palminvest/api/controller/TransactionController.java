package com.palminvest.api.controller;

import com.palminvest.api.model.Transaction;
import com.palminvest.api.repository.TransactionRepository;
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
    public List<Transaction> listarTodas() {
        return repository.findAll();
    }

    // Rota POST: Recebe uma transação nova do Front e salva no banco
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction criarTransacao(@RequestBody Transaction transaction) {
        return repository.save(transaction);
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
}