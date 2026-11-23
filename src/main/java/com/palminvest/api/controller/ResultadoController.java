package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosLimiteIsencaoDTO;
import com.palminvest.api.dto.DadosResultadosAnoDTO;
import com.palminvest.api.infra.ParametroInvalidoException;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.service.ResultadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

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

    // GET /api/resultados/limite-isencao?mes=2025-03: vendido no mês e quanto ainda cabe na isenção (ações e cripto)
    @GetMapping("/limite-isencao")
    public DadosLimiteIsencaoDTO limiteDeIsencao(@RequestParam String mes, @AuthenticationPrincipal Usuario usuario) {
        YearMonth mesPedido;
        try {
            mesPedido = YearMonth.parse(mes.trim());
        } catch (DateTimeParseException formatoInvalido) {
            throw new ParametroInvalidoException("mes", "use o formato AAAA-MM, por exemplo 2025-03");
        }
        ProventoController.validarAno(mesPedido.getYear());
        return service.limiteDeIsencao(usuario, mesPedido);
    }
}
