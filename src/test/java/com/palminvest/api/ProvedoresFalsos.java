package com.palminvest.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Servidor HTTP local que faz o papel da Brapi e da CoinGecko nos testes: nenhuma chamada sai para a internet
 * e nenhum segredo é necessário (o CI roda sem chaves). Cada teste diz o que cada caminho responde e depois
 * confere as requisições recebidas (caminho, query e headers). Caminho sem resposta configurada devolve 501,
 * para uma chamada inesperada aparecer como falha no teste.
 */
public final class ProvedoresFalsos {

    private final HttpServer servidor;
    private final Map<String, Function<Requisicao, Resposta>> rotas = new ConcurrentHashMap<>();
    private final List<Requisicao> recebidas = new CopyOnWriteArrayList<>();

    private ProvedoresFalsos() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
        // Uma thread virtual por requisição: uma resposta lenta (teste de timeout) não segura as outras
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public static ProvedoresFalsos iniciar() {
        return new ProvedoresFalsos();
    }

    // URL base de um provedor falso, ex: url("/brapi") -> http://127.0.0.1:54321/brapi
    public String url(String prefixo) {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + prefixo;
    }

    // Resposta fixa para um caminho exato (ex: "/brapi/quote/PETR4")
    public void quando(String caminho, int status, String json) {
        rotas.put(caminho, requisicao -> new Resposta(status, json));
    }

    // Resposta calculada a partir da requisição (ex: depende do ?query= ou demora de propósito)
    public void quando(String caminho, Function<Requisicao, Resposta> resposta) {
        rotas.put(caminho, resposta);
    }

    public List<Requisicao> requisicoes() {
        return List.copyOf(recebidas);
    }

    public List<Requisicao> requisicoes(String caminho) {
        return recebidas.stream().filter(r -> r.caminho().equals(caminho)).toList();
    }

    public void limpar() {
        rotas.clear();
        recebidas.clear();
    }

    private void atender(HttpExchange troca) throws IOException {
        var headers = new HashMap<String, String>();
        troca.getRequestHeaders().forEach((nome, valores) -> headers.put(nome.toLowerCase(Locale.ROOT), String.join(",", valores)));
        var requisicao = new Requisicao(troca.getRequestURI().getPath(), lerQuery(troca.getRequestURI().getRawQuery()),
                Collections.unmodifiableMap(headers));
        recebidas.add(requisicao);

        var rota = rotas.get(requisicao.caminho());
        var resposta = rota == null
                ? new Resposta(501, "{\"teste\":\"caminho sem resposta configurada: " + requisicao.caminho() + "\"}")
                : rota.apply(requisicao);

        var corpo = resposta.json().getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(resposta.status(), corpo.length);
        try (var saida = troca.getResponseBody()) {
            saida.write(corpo);
        }
    }

    private static Map<String, String> lerQuery(String query) {
        var parametros = new HashMap<String, String>();
        if (query == null) {
            return parametros;
        }
        for (var par : query.split("&")) {
            var partes = par.split("=", 2);
            parametros.put(URLDecoder.decode(partes[0], StandardCharsets.UTF_8),
                    partes.length > 1 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "");
        }
        return parametros;
    }

    // headers com nomes em minúsculas
    public record Requisicao(String caminho, Map<String, String> query, Map<String, String> headers) {
    }

    public record Resposta(int status, String json) {
        // Resposta que demora antes de sair, para testar o timeout de leitura
        public static Resposta comAtraso(long milissegundos, int status, String json) {
            try {
                Thread.sleep(milissegundos);
            } catch (InterruptedException interrompido) {
                Thread.currentThread().interrupt();
            }
            return new Resposta(status, json);
        }
    }
}
