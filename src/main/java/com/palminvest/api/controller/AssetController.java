package com.palminvest.api.controller;

import com.palminvest.api.dto.DadosBuscaAtivosDTO;
import com.palminvest.api.infra.ParametroInvalidoException;
import com.palminvest.api.model.AssetClassFilter;
import com.palminvest.api.service.AssetSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Busca de ativos nos provedores de mercado (Brapi e CoinGecko), feita pela API para o front não chamá-los direto
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    static final int TAMANHO_MINIMO_BUSCA = 2;
    static final int TAMANHO_MAXIMO_BUSCA = 50;

    @Autowired
    private AssetSearchService service;

    // GET /api/assets/search?q=petr&class=ALL|STOCK|FII|ETF|BDR|CRYPTO
    @GetMapping("/search")
    public DadosBuscaAtivosDTO buscar(@RequestParam String q,
                                      @RequestParam(name = "class", defaultValue = "ALL") AssetClassFilter classe) {
        var termo = q.trim();
        if (termo.length() < TAMANHO_MINIMO_BUSCA || termo.length() > TAMANHO_MAXIMO_BUSCA) {
            throw new ParametroInvalidoException("q", "informe de %d a %d caracteres".formatted(TAMANHO_MINIMO_BUSCA, TAMANHO_MAXIMO_BUSCA));
        }
        return service.buscar(termo, classe);
    }
}
