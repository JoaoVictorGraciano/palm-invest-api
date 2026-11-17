package com.palminvest.api.service;

import com.palminvest.api.infra.cotacao.CoinGeckoClient;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

// Símbolo da carteira (ex: "ENA") para o id usado nas cotações da CoinGecko (ex: "ethena"),
// pela mesma regra das sugestões: entre as moedas com aquele símbolo, a de maior valor de mercado
@Component
public class CoinGeckoIdResolver {

    private final CoinGeckoClient coinGecko;

    public CoinGeckoIdResolver(CoinGeckoClient coinGecko) {
        this.coinGecko = coinGecko;
    }

    // Vazio se nenhuma moeda tem exatamente esse símbolo; lança ProvedorIndisponivelException se a CoinGecko falhar
    public Optional<String> resolver(String simbolo) {
        var alvo = simbolo.trim().toUpperCase(Locale.ROOT);
        var moeda = AssetSearchService.melhorMoedaPorSimbolo(coinGecko.buscar(alvo)).get(alvo);
        return Optional.ofNullable(moeda).map(CoinGeckoClient.Moeda::id);
    }
}
