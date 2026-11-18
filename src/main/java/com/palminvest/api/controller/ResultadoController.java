package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosResultadosAnoDTO;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.service.ResultadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Lucro realizado e estimativa de imposto de renda do usuário do token (estimativa; não substitui um contador)
@RestController
@RequestMapping("/api/resultados")
public class ResultadoController {

    @Autowired
    private ResultadoService service;

    // GET /api/resultados?ano=2025: mês a mês e por categoria, total do ano, prejuízo acumulado e alertas
    @GetMapping
    public DadosResultadosAnoDTO doAno(@RequestParam int ano, @AuthenticationPrincipal Usuario usuario) {
        ProventoController.validarAno(ano);
        return service.doAno(usuario, ano);
    }
}
