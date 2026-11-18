package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosCadastroProventoDTO;
import com.palminvest.api.dto.DadosDetalhamentoProventoDTO;
import com.palminvest.api.infra.ParametroInvalidoException;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.service.ProventoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

// Proventos do usuário do token: ninguém enxerga nem altera provento de outra pessoa (404)
@RestController
@RequestMapping("/api/proventos")
public class ProventoController {

    static final int ANO_MINIMO = 1900;
    static final int ANO_MAXIMO = 2100;

    @Autowired
    private ProventoService service;

    // GET /api/proventos?ano=2025&assetSymbol=PETR4 (filtros opcionais), do pagamento mais recente para o mais antigo
    @GetMapping
    public List<DadosDetalhamentoProventoDTO> listar(@RequestParam(required = false) Integer ano,
                                                     @RequestParam(required = false) String assetSymbol,
                                                     @AuthenticationPrincipal Usuario usuario) {
        validarAno(ano);
        var simbolo = assetSymbol == null || assetSymbol.isBlank() ? null : assetSymbol.trim().toUpperCase(Locale.ROOT);
        return service.listar(usuario, ano, simbolo).stream().map(DadosDetalhamentoProventoDTO::new).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DadosDetalhamentoProventoDTO criar(@RequestBody @Valid DadosCadastroProventoDTO dados,
                                              @AuthenticationPrincipal Usuario usuario) {
        return new DadosDetalhamentoProventoDTO(service.criar(dados, usuario));
    }

    // PUT substitui todos os campos (404 se não existir ou for de outro usuário)
    @PutMapping("/{id}")
    public ResponseEntity<DadosDetalhamentoProventoDTO> atualizar(@PathVariable Long id,
                                                                  @RequestBody @Valid DadosCadastroProventoDTO dados,
                                                                  @AuthenticationPrincipal Usuario usuario) {
        return service.atualizar(id, dados, usuario)
                .map(provento -> ResponseEntity.ok(new DadosDetalhamentoProventoDTO(provento)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return service.excluir(id, usuario)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    static void validarAno(Integer ano) {
        if (ano != null && (ano < ANO_MINIMO || ano > ANO_MAXIMO)) {
            throw new ParametroInvalidoException("ano", "informe um ano entre %d e %d".formatted(ANO_MINIMO, ANO_MAXIMO));
        }
    }
}
