package com.palminvest.api.controller;

import com.palminvest.api.IntegracaoTestBase;
import com.palminvest.api.ProvedoresFalsos.Requisicao;
import com.palminvest.api.ProvedoresFalsos.Resposta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.palminvest.api.RespostasDeMercado.buscaCoinGecko;
import static com.palminvest.api.RespostasDeMercado.cotacaoBrapi;
import static com.palminvest.api.RespostasDeMercado.moeda;
import static com.palminvest.api.RespostasDeMercado.precosCoinGecko;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /api/quotes: cotação em reais na Brapi (B3) e na CoinGecko (cripto), com resultado parcial e cache compartilhado
@ExtendWith(OutputCaptureExtension.class)
class QuoteControllerTest extends IntegracaoTestBase {

    private static final String BUSCA_COINGECKO = "/coingecko/search";
    private static final String PRECOS_COINGECKO = "/coingecko/simple/price";
    private static final String HORARIO_BRAPI = "2026-11-17T17:30:00.000Z";

    private String token;
    // Respostas de /coingecko/search por ?query=, como a CoinGecko real (o resolvedor busca pelo símbolo)
    private final Map<String, String> buscasCoinGecko = new HashMap<>();

    @BeforeEach
    void prepararProvedores() throws Exception {
        token = novoUsuarioComToken("A", "a@exemplo.com");
        buscasCoinGecko.clear();
        buscasCoinGecko.put("BTC", buscaCoinGecko(
                moeda("bitcoin", "Bitcoin", "BTC", 1),
                moeda("batcat", "batcat", "BTC", 3200)));
        buscasCoinGecko.put("ENA", buscaCoinGecko(
                moeda("ethena-usde", "Ethena USDe", "USDE", 31),
                moeda("ethena", "Ethena", "ENA", 58),
                moeda("ena-clone", "ENA Clone", "ENA", null)));
        PROVEDORES.quando(BUSCA_COINGECKO, r -> new Resposta(200, buscasCoinGecko.getOrDefault(r.query().get("query"), buscaCoinGecko())));
        PROVEDORES.quando("/brapi/quote/PETR4", 200, cotacaoBrapi("PETR4", "38.25", HORARIO_BRAPI));
        PROVEDORES.quando("/brapi/quote/HGLG11", 200, cotacaoBrapi("HGLG11", "158.40", HORARIO_BRAPI));
        PROVEDORES.quando(PRECOS_COINGECKO, r -> new Resposta(200, precosDosIds(r)));
    }

    // --- autenticação e validação ---

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/quotes").param("symbols", "PETR4"))
                .andExpect(status().isUnauthorized());
        assertTrue(PROVEDORES.requisicoes().isEmpty());
    }

    @Test
    void simbolosAusentesVaziosInvalidosOuDemaisRetornam400() throws Exception {
        mockMvc.perform(get("/api/quotes").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("symbols"));
        cotar(" , ").andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("symbols"));
        cotar("PETR4,PETR-4")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("symbols"))
                .andExpect(jsonPath("$[0].mensagem").value(containsString("PETR-4")));
        cotar(IntStream.range(0, 51).mapToObj(i -> "AB" + i).collect(Collectors.joining(",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("symbols"));
        assertTrue(PROVEDORES.requisicoes().isEmpty());
    }

    // --- cotação ---

    @Test
    void cotaAcoesNaBrapiECriptosNaCoinGeckoEmReaisNaOrdemPedida() throws Exception {
        cotar("petr4, HGLG11,BTC,ENA,PETR4")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes", hasSize(4)))
                .andExpect(jsonPath("$.quotes[*].symbol").value(org.hamcrest.Matchers.contains("PETR4", "HGLG11", "BTC", "ENA")))
                .andExpect(jsonPath("$.quotes[0].price").value(38.25))
                .andExpect(jsonPath("$.quotes[0].currency").value("BRL"))
                .andExpect(jsonPath("$.quotes[0].provider").value("brapi"))
                .andExpect(jsonPath("$.quotes[0].updatedAt").value("2026-11-17T17:30:00Z"))
                .andExpect(jsonPath("$.quotes[1].price").value(158.4))
                .andExpect(jsonPath("$.quotes[2].price").value(512345.67))
                .andExpect(jsonPath("$.quotes[2].provider").value("coingecko"))
                .andExpect(jsonPath("$.quotes[3].price").value(2.15))
                .andExpect(jsonPath("$.failures", hasSize(0)));

        // Todas as criptos numa única chamada; o id é o da moeda de maior valor de mercado com aquele símbolo
        var precos = PROVEDORES.requisicoes(PRECOS_COINGECKO);
        assertEquals(1, precos.size());
        assertEquals("bitcoin,ethena", precos.getFirst().query().get("ids"));
        assertEquals("brl", precos.getFirst().query().get("vs_currencies"));
        assertEquals(COINGECKO_KEY_TESTE, precos.getFirst().headers().get("x-cg-demo-api-key"));
        // Uma chamada por ativo na Brapi (o símbolo repetido conta uma vez)
        assertEquals(1, PROVEDORES.requisicoes("/brapi/quote/PETR4").size());
        assertEquals("Bearer " + BRAPI_TOKEN_TESTE, PROVEDORES.requisicoes("/brapi/quote/PETR4").getFirst().headers().get("authorization"));
    }

    @Test
    void precoComCasasDecimaisVoltaSemPerda() throws Exception {
        PROVEDORES.quando("/brapi/quote/MGLU3", 200, cotacaoBrapi("MGLU3", "9.87654321", HORARIO_BRAPI));

        var json = cotar("MGLU3").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(0, new BigDecimal("9.87654321").compareTo(jsonMapper.readTree(json).at("/quotes/0/price").decimalValue()));
    }

    @Test
    void usaAClasseRegistradaPeloUsuarioParaEscolherOProvedor() throws Exception {
        // ABCD12 tem cara de ticker da B3, mas o usuário A registrou como cripto
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("1")));
        corpo.put("assetSymbol", "ABCD12");
        corpo.put("assetClass", "CRYPTO");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated());
        buscasCoinGecko.put("ABCD12", buscaCoinGecko(moeda("abcd-coin", "Abcd", "ABCD12", 900)));
        PROVEDORES.quando("/brapi/quote/ABCD12", 404, "{\"error\":true,\"message\":\"Não encontramos a ação ABCD12\"}");

        cotar("ABCD12").andExpect(status().isOk()).andExpect(jsonPath("$.quotes[0].provider").value("coingecko"));
        assertTrue(PROVEDORES.requisicoes("/brapi/quote/ABCD12").isEmpty());

        // Para B, que não tem ABCD12, vale o formato de ticker da B3: Brapi, que não conhece o ativo
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");
        mockMvc.perform(get("/api/quotes").param("symbols", "ABCD12").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes", hasSize(0)))
                .andExpect(jsonPath("$.failures[0].provider").value("brapi"))
                .andExpect(jsonPath("$.failures[0].mensagem").value("Cotação não encontrada na Brapi"));
    }

    // --- falhas ---

    @Test
    void provedorForaDoArDevolveResultadoParcial() throws Exception {
        PROVEDORES.quando("/brapi/quote/PETR4", 500, "{}");

        cotar("PETR4,BTC")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes", hasSize(1)))
                .andExpect(jsonPath("$.quotes[0].symbol").value("BTC"))
                .andExpect(jsonPath("$.failures[0].symbol").value("PETR4"))
                .andExpect(jsonPath("$.failures[0].provider").value("brapi"))
                .andExpect(jsonPath("$.failures[0].mensagem").value("Brapi respondeu com erro (HTTP 500)"));
    }

    @Test
    void simboloInexistenteNaoEhIndisponibilidadeERetorna200() throws Exception {
        PROVEDORES.quando("/brapi/quote/XXXX3", 404, "{\"error\":true,\"message\":\"Não encontramos a ação XXXX3\"}");

        cotar("XXXX3,ZZZQ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes", hasSize(0)))
                .andExpect(jsonPath("$.failures[0].mensagem").value("Cotação não encontrada na Brapi"))
                .andExpect(jsonPath("$.failures[1].symbol").value("ZZZQ"))
                .andExpect(jsonPath("$.failures[1].mensagem").value("Criptomoeda não encontrada na CoinGecko"));
    }

    @Test
    void tudoIndisponivelRetorna503ComMensagem() throws Exception {
        PROVEDORES.quando("/brapi/quote/PETR4", 502, "{}");
        PROVEDORES.quando(BUSCA_COINGECKO, 429, "{\"status\":{\"error_code\":429}}");

        cotar("PETR4,BTC")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value(containsString("Brapi respondeu com erro (HTTP 502)")))
                .andExpect(jsonPath("$.mensagem").value(containsString("CoinGecko recusou a consulta: limite de requisições atingido")));
    }

    @Test
    void provedorLentoViraFalhaPorTimeoutSemSegurarOsOutros() throws Exception {
        PROVEDORES.quando("/brapi/quote/PETR4", r -> Resposta.comAtraso(1500, 200, cotacaoBrapi("PETR4", "38.25", HORARIO_BRAPI)));

        cotar("PETR4,HGLG11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes[0].symbol").value("HGLG11"))
                .andExpect(jsonPath("$.failures[0].symbol").value("PETR4"))
                .andExpect(jsonPath("$.failures[0].mensagem").value("Brapi não respondeu a tempo"));
    }

    @Test
    void falhaDoPrecoDaCoinGeckoMarcaTodasAsCriptosComoIndisponiveis() throws Exception {
        PROVEDORES.quando(PRECOS_COINGECKO, 503, "{}");

        cotar("PETR4,BTC,ENA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes[*].symbol").value(org.hamcrest.Matchers.contains("PETR4")))
                .andExpect(jsonPath("$.failures[*].symbol").value(org.hamcrest.Matchers.contains("BTC", "ENA")))
                .andExpect(jsonPath("$.failures[0].mensagem").value("CoinGecko respondeu com erro (HTTP 503)"));
    }

    // --- cache ---

    @Test
    void cotacoesFicamEmCacheCompartilhadoEntreUsuarios() throws Exception {
        cotar("PETR4,BTC").andExpect(status().isOk());
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");
        mockMvc.perform(get("/api/quotes").param("symbols", "BTC,PETR4").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes", hasSize(2)));

        assertEquals(1, PROVEDORES.requisicoes("/brapi/quote/PETR4").size());
        assertEquals(1, PROVEDORES.requisicoes(BUSCA_COINGECKO).size());
        assertEquals(1, PROVEDORES.requisicoes(PRECOS_COINGECKO).size());
    }

    @Test
    void criptoJaEmCacheNaoEntraNaProximaChamadaDePrecos() throws Exception {
        cotar("BTC").andExpect(status().isOk());
        cotar("BTC,ENA").andExpect(status().isOk()).andExpect(jsonPath("$.quotes", hasSize(2)));

        var precos = PROVEDORES.requisicoes(PRECOS_COINGECKO);
        assertEquals(2, precos.size());
        assertEquals("ethena", precos.get(1).query().get("ids"));
    }

    @Test
    void falhaNaoFicaNoCache() throws Exception {
        PROVEDORES.quando("/brapi/quote/PETR4", 500, "{}");
        cotar("PETR4").andExpect(status().isServiceUnavailable());

        PROVEDORES.quando("/brapi/quote/PETR4", 200, cotacaoBrapi("PETR4", "38.25", HORARIO_BRAPI));
        cotar("PETR4").andExpect(status().isOk()).andExpect(jsonPath("$.quotes[0].price").value(38.25));

        assertEquals(2, PROVEDORES.requisicoes("/brapi/quote/PETR4").size());
    }

    @Test
    void idAprendidoNaBuscaEvitaNovaBuscaNaCotacao() throws Exception {
        mockMvc.perform(get("/api/assets/search").param("q", "ENA").param("class", "CRYPTO").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].symbol").value("ENA"));

        cotar("ENA").andExpect(status().isOk()).andExpect(jsonPath("$.quotes[0].price").value(2.15));

        assertEquals(1, PROVEDORES.requisicoes(BUSCA_COINGECKO).size());
        assertEquals("ethena", PROVEDORES.requisicoes(PRECOS_COINGECKO).getFirst().query().get("ids"));
    }

    // --- log de consumo ---

    @Test
    void registraCadaChamadaAosProvedoresSemAsChaves(CapturedOutput saida) throws Exception {
        cotar("PETR4,BTC").andExpect(status().isOk());

        assertTrue(saida.getOut().contains("Chamada à Brapi nº"), "deveria registrar a chamada à Brapi");
        assertTrue(saida.getOut().contains("GET /brapi/quote/PETR4 -> HTTP 200"), "deveria registrar método, caminho e status");
        assertTrue(saida.getOut().contains("Chamada à CoinGecko nº"), "deveria registrar as chamadas à CoinGecko");
        assertTrue(saida.getOut().contains("total desde a subida: Brapi"), "deveria registrar o total por provedor");
        assertFalse(saida.getAll().contains(BRAPI_TOKEN_TESTE), "o token da Brapi não pode aparecer em log");
        assertFalse(saida.getAll().contains(COINGECKO_KEY_TESTE), "a chave da CoinGecko não pode aparecer em log");
    }

    private ResultActions cotar(String simbolos) throws Exception {
        return mockMvc.perform(get("/api/quotes").param("symbols", simbolos).header("Authorization", "Bearer " + token));
    }

    // /simple/price devolve só os ids pedidos, como a CoinGecko real
    private static String precosDosIds(Requisicao requisicao) {
        var conhecidos = Map.of("bitcoin", "512345.67", "ethena", "2.15", "abcd-coin", "1.5");
        var pares = java.util.Arrays.stream(requisicao.query().getOrDefault("ids", "").split(","))
                .filter(conhecidos::containsKey)
                .flatMap(id -> java.util.stream.Stream.of(id, conhecidos.get(id)))
                .toArray(String[]::new);
        return precosCoinGecko(pares);
    }
}
