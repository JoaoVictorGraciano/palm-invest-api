package com.palminvest.api.service;

import com.palminvest.api.infra.cotacao.CachesDeMercado;
import com.palminvest.api.infra.cotacao.CoinGeckoClient;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

// Símbolo da carteira (ex: "ENA") para o id usado nas cotações da CoinGecko (ex: "ethena"),
// pela mesma regra das sugestões: entre as moedas com aquele símbolo, a de maior valor de mercado.
// O mapa fica em cache (24 h por padrão) e também é alimentado pelas buscas de cripto.
@Component
public class CoinGeckoIdResolver {

    private final CoinGeckoClient coinGecko;
    private final CachesDeMercado caches;

    public CoinGeckoIdResolver(CoinGeckoClient coinGecko, CachesDeMercado caches) {
        this.coinGecko = coinGecko;
        this.caches = caches;
    }

    // Vazio se nenhuma moeda tem exatamente esse símbolo; lança ProvedorIndisponivelException se a CoinGecko falhar
    public Optional<String> resolver(String simbolo) {
        var alvo = simbolo.trim().toUpperCase(Locale.ROOT);
        return caches.idCripto(alvo).orElseGet(() -> {
            var moeda = AssetSearchService.melhorMoedaPorSimbolo(coinGecko.buscar(alvo)).get(alvo);
            var id = Optional.ofNullable(moeda).map(CoinGeckoClient.Moeda::id);
            caches.guardarIdCripto(alvo, id);
            return id;
        });
    }
}
