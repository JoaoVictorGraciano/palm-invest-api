package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosCotacoesDTO;
import com.palminvest.api.infra.ParametroInvalidoException;
import com.palminvest.api.model.Usuario;
import com.palminvest.api.service.QuoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// Cotações atuais em reais, buscadas pela API nos provedores de mercado para o front não chamá-los direto
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    static final int MAXIMO_SIMBOLOS = 50;
    private static final Pattern SIMBOLO_VALIDO = Pattern.compile("^[A-Z0-9]{1,20}$");

    @Autowired
    private QuoteService service;

    // GET /api/quotes?symbols=PETR4,HGLG11,BTC,ENA
    @GetMapping
    public DadosCotacoesDTO cotar(@RequestParam String symbols, @AuthenticationPrincipal Usuario usuario) {
        // Mesma normalização do assetSymbol das transações; repetidos contam uma vez, na ordem pedida
        var simbolos = new LinkedHashSet<String>();
        Arrays.stream(symbols.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .forEach(simbolos::add);

        if (simbolos.isEmpty()) {
            throw new ParametroInvalidoException("symbols", "informe ao menos um símbolo");
        }
        if (simbolos.size() > MAXIMO_SIMBOLOS) {
            throw new ParametroInvalidoException("symbols", "no máximo %d símbolos por consulta".formatted(MAXIMO_SIMBOLOS));
        }
        var invalidos = simbolos.stream().filter(s -> !SIMBOLO_VALIDO.matcher(s).matches()).toList();
        if (!invalidos.isEmpty()) {
            throw new ParametroInvalidoException("symbols", "símbolo inválido: " + String.join(", ", invalidos));
        }
        return service.cotar(List.copyOf(simbolos), usuario);
    }
}
