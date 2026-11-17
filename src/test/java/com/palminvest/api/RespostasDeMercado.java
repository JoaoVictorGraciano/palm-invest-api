package com.palminvest.api;

import java.util.Arrays;
import java.util.stream.Collectors;

// JSON no formato da Brapi e da CoinGecko, para os testes montarem as respostas do ProvedoresFalsos
public final class RespostasDeMercado {

    private RespostasDeMercado() {
    }

    // Item de GET /quote/list da Brapi
    public static String ativo(String stock, String name, String type, String subType) {
        return "{\"stock\":%s,\"name\":%s,\"type\":%s,\"subType\":%s}".formatted(texto(stock), texto(name), texto(type), texto(subType));
    }

    public static String listaBrapi(String... ativos) {
        return "{\"stocks\":[" + String.join(",", ativos) + "]}";
    }

    // Corpo de GET /quote/{simbolo} da Brapi
    public static String cotacaoBrapi(String simbolo, String preco, String horario) {
        return "{\"results\":[{\"symbol\":\"%s\",\"shortName\":\"%s\",\"regularMarketPrice\":%s,\"currency\":\"BRL\",\"regularMarketTime\":\"%s\"}]}"
                .formatted(simbolo, simbolo, preco, horario);
    }

    // Item de GET /search da CoinGecko
    public static String moeda(String id, String name, String symbol, Integer marketCapRank) {
        return "{\"id\":%s,\"name\":%s,\"api_symbol\":%s,\"symbol\":%s,\"market_cap_rank\":%s,\"thumb\":\"x\"}"
                .formatted(texto(id), texto(name), texto(id), texto(symbol), marketCapRank == null ? "null" : marketCapRank);
    }

    public static String buscaCoinGecko(String... moedas) {
        return "{\"coins\":[" + String.join(",", moedas) + "],\"exchanges\":[],\"categories\":[]}";
    }

    // Corpo de GET /simple/price da CoinGecko: pares id, preço em reais (last_updated_at fixo)
    public static String precosCoinGecko(String... idEPreco) {
        var pares = new String[idEPreco.length / 2];
        for (int i = 0; i < pares.length; i++) {
            pares[i] = "\"%s\":{\"brl\":%s,\"last_updated_at\":1763400000}".formatted(idEPreco[2 * i], idEPreco[2 * i + 1]);
        }
        return "{" + Arrays.stream(pares).collect(Collectors.joining(",")) + "}";
    }

    private static String texto(String valor) {
        return valor == null ? "null" : "\"" + valor.replace("\"", "\\\"") + "\"";
    }
}
