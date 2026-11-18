package com.palminvest.api.controller;

import com.palminvest.api.IntegracaoTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.stream.StreamSupport;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Lucro realizado (custo médio com taxas), estimativa de IR por mês e categoria, compensação, limite de isenção e alertas.
// Os valores esperados foram calculados à mão em cada teste.
class ResultadoControllerTest extends IntegracaoTestBase {

    private String tokenA;

    @BeforeEach
    void logar() throws Exception {
        tokenA = novoUsuarioComToken("A", "a@exemplo.com");
    }

    // --- Lucro realizado nas transações ---

    @Test
    void lucroEPrejuizoRealizadosPeloCustoMedioComTaxasEVendaParcial() throws Exception {
        // Custo médio: (100 × 10 + 5) + (100 × 12 + 5) = 2.210 / 200 = 11,05
        operar(tokenA, "BUY", "PETR4", "STOCK", "100", "10", "5", "2025-01-10T10:00:00");
        operar(tokenA, "BUY", "PETR4", "STOCK", "100", "12", "5", "2025-01-15T10:00:00");
        // Venda parcial: (50 × 15 − 2,50) − 50 × 11,05 = 747,50 − 552,50 = 195,00
        var vendaParcial = operar(tokenA, "SELL", "PETR4", "STOCK", "50", "15", "2.50", "2025-02-03T10:00:00");
        assertDecimal("195.00", vendaParcial.get("lucroRealizado"));
        // Resto com prejuízo: (150 × 10 − 3) − 150 × 11,05 = 1.497 − 1.657,50 = −160,50
        operar(tokenA, "SELL", "PETR4", "STOCK", "150", "10", "3", "2025-03-03T10:00:00");
        // Posição zerada: o custo médio recomeça. (10 × 21) − 10 × 20 = 10,00
        operar(tokenA, "BUY", "PETR4", "STOCK", "10", "20", "0", "2025-04-01T10:00:00");
        operar(tokenA, "SELL", "PETR4", "STOCK", "10", "21", "0", "2025-04-02T10:00:00");

        var lista = json(mockMvc.perform(get("/api/transactions").header("Authorization", "Bearer " + tokenA)).andExpect(status().isOk()));
        // Mais recente primeiro: venda 04/04, compra 04/01, venda 03/03, venda 02/03, compras
        assertDecimal("10.00", lista.get(0).get("lucroRealizado"));
        assertTrue(lista.get(1).get("lucroRealizado").isNull(), "compra não tem lucro realizado");
        assertDecimal("-160.50", lista.get(2).get("lucroRealizado"));
        assertDecimal("195.00", lista.get(3).get("lucroRealizado"));
        assertTrue(lista.get(4).get("lucroRealizado").isNull());
        assertTrue(lista.get(5).get("lucroRealizado").isNull());
    }

    // --- Isenção e alíquotas ---

    @Test
    void acoesIsentasAte20MilEmVendasETributadasAcima() throws Exception {
        // Março: vendas de R$ 19.999,00 -> isento
        operar(tokenA, "BUY", "PETR4", "STOCK", "1000", "10", "0", "2025-03-03T10:00:00");
        operar(tokenA, "SELL", "PETR4", "STOCK", "1000", "19.999", "0", "2025-03-20T10:00:00");
        // Abril: vendas de R$ 20.001,00 -> lucro do mês inteiro tributado: 10.001,00 × 15% = 1.500,15
        operar(tokenA, "BUY", "VALE3", "STOCK", "1000", "10", "0", "2025-04-01T10:00:00");
        operar(tokenA, "SELL", "VALE3", "STOCK", "1000", "20.001", "0", "2025-04-22T10:00:00");
        // Maio: vendas de exatamente R$ 20.000,00 -> "até R$ 20 mil" ainda é isento
        operar(tokenA, "BUY", "ITUB4", "STOCK", "1000", "10", "0", "2025-05-02T10:00:00");
        operar(tokenA, "SELL", "ITUB4", "STOCK", "1000", "20", "0", "2025-05-20T10:00:00");

        var resultado = resultados(tokenA, 2025);

        var marco = categoria(resultado, 3, "STOCK");
        assertDecimal("19999.00", marco.get("totalVendas"));
        assertDecimal("9999.00", marco.get("lucroBruto"));
        assertTrue(marco.get("isento").asBoolean());
        assertDecimal("0.00", marco.get("baseCalculo"));
        assertDecimal("0.00", marco.get("impostoEstimado"));

        var abril = categoria(resultado, 4, "STOCK");
        assertDecimal("20001.00", abril.get("totalVendas"));
        assertFalse(abril.get("isento").asBoolean());
        assertDecimal("10001.00", abril.get("baseCalculo"));
        assertDecimal("0.15", abril.get("aliquota"));
        assertDecimal("1500.15", abril.get("impostoEstimado"));
        assertEquals("6015", abril.get("codigoDarf").asString());
        assertEquals("2025-05-30", abril.get("vencimentoDarf").asString()); // 31/05/2025 é sábado
        assertDecimal("1500.15", resultado.at("/meses/3/darfs/0/impostoEstimado"));

        assertTrue(categoria(resultado, 5, "STOCK").get("isento").asBoolean());
        assertDecimal("1500.15", resultado.at("/totalAno/impostoEstimado"));
    }

    @Test
    void criptoIsentaAte35MilSomandoTodasAsMoedas() throws Exception {
        // Maio: R$ 35.000,00 em vendas de cripto -> isento
        operar(tokenA, "BUY", "BTC", "CRYPTO", "1", "20000", "0", "2025-05-02T10:00:00");
        operar(tokenA, "SELL", "BTC", "CRYPTO", "1", "35000", "0", "2025-05-20T10:00:00");
        // Junho: BTC R$ 20.000,00 + ETH R$ 15.000,01 = R$ 35.000,01 -> tributado
        // lucro 10.000,00 + 5.000,01 = 15.000,01 × 15% = 2.250,00
        operar(tokenA, "BUY", "BTC", "CRYPTO", "1", "10000", "0", "2025-06-02T10:00:00");
        operar(tokenA, "BUY", "ETH", "CRYPTO", "1", "10000", "0", "2025-06-02T10:00:00");
        operar(tokenA, "SELL", "BTC", "CRYPTO", "1", "20000", "0", "2025-06-20T10:00:00");
        operar(tokenA, "SELL", "ETH", "CRYPTO", "1", "15000.01", "0", "2025-06-20T10:00:00");

        var resultado = resultados(tokenA, 2025);

        var maio = categoria(resultado, 5, "CRYPTO");
        assertDecimal("35000.00", maio.get("totalVendas"));
        assertTrue(maio.get("isento").asBoolean());
        assertDecimal("0.00", maio.get("impostoEstimado"));

        var junho = categoria(resultado, 6, "CRYPTO");
        assertDecimal("35000.01", junho.get("totalVendas"));
        assertFalse(junho.get("isento").asBoolean());
        assertDecimal("15000.01", junho.get("baseCalculo"));
        assertDecimal("2250.00", junho.get("impostoEstimado"));
        assertEquals("4600", junho.get("codigoDarf").asString());
        assertEquals("2025-07-31", junho.get("vencimentoDarf").asString());
    }

    @Test
    void fiiEtfEBdrNaoTemIsencaoESomamNoMesmoDarf() throws Exception {
        // Vendas pequenas, mas sem isenção: FII 20%, ETF e BDR 15%
        operar(tokenA, "BUY", "HGLG11", "FII", "10", "100", "0", "2025-07-01T10:00:00");
        operar(tokenA, "SELL", "HGLG11", "FII", "10", "110", "0", "2025-07-15T10:00:00");   // lucro 100 -> 20,00
        operar(tokenA, "BUY", "BOVA11", "ETF", "10", "100", "0", "2025-07-01T10:00:00");
        operar(tokenA, "SELL", "BOVA11", "ETF", "10", "120", "0", "2025-07-15T10:00:00");   // lucro 200 -> 30,00
        operar(tokenA, "BUY", "AAPL34", "BDR", "10", "50", "0", "2025-07-01T10:00:00");
        operar(tokenA, "SELL", "AAPL34", "BDR", "10", "60", "0", "2025-07-15T10:00:00");    // lucro 100 -> 15,00

        var resultado = resultados(tokenA, 2025);

        var fii = categoria(resultado, 7, "FII");
        assertFalse(fii.get("isento").asBoolean());
        assertDecimal("0.20", fii.get("aliquota"));
        assertDecimal("20.00", fii.get("impostoEstimado"));
        var etf = categoria(resultado, 7, "ETF");
        assertFalse(etf.get("isento").asBoolean());
        assertDecimal("30.00", etf.get("impostoEstimado"));
        assertDecimal("15.00", categoria(resultado, 7, "BDR").get("impostoEstimado"));
        // Um DARF 6015 no mês com as três categorias
        assertEquals(1, resultado.at("/meses/6/darfs").size());
        assertEquals("6015", resultado.at("/meses/6/darfs/0/codigoDarf").asString());
        assertDecimal("65.00", resultado.at("/meses/6/darfs/0/impostoEstimado"));
        assertDecimal("65.00", resultado.at("/meses/6/impostoEstimado"));
    }

    // --- Compensação de prejuízo ---

    @Test
    void prejuizoAbateLucroDosMesesSeguintesDoMesmoGrupo() throws Exception {
        // Janeiro: ações -10.000 (vendas de 30 mil, não isento) e FII -100
        operar(tokenA, "BUY", "PETR4", "STOCK", "2000", "20", "0", "2025-01-02T10:00:00");
        operar(tokenA, "SELL", "PETR4", "STOCK", "2000", "15", "0", "2025-01-20T10:00:00");
        operar(tokenA, "BUY", "HGLG11", "FII", "10", "100", "0", "2025-01-02T10:00:00");
        operar(tokenA, "SELL", "HGLG11", "FII", "10", "90", "0", "2025-01-20T10:00:00");
        // Fevereiro: BDR +3.000, totalmente abatido pelo prejuízo de ações (mesmo grupo); saldo 7.000
        operar(tokenA, "BUY", "AAPL34", "BDR", "100", "50", "0", "2025-02-03T10:00:00");
        operar(tokenA, "SELL", "AAPL34", "BDR", "100", "80", "0", "2025-02-20T10:00:00");
        // Março: ações +10.000 (não isento) abate 7.000 -> base 3.000 -> 450,00; FII +300 abate 100 -> base 200 -> 40,00
        operar(tokenA, "BUY", "VALE3", "STOCK", "2000", "10", "0", "2025-03-03T10:00:00");
        operar(tokenA, "SELL", "VALE3", "STOCK", "2000", "15", "0", "2025-03-20T10:00:00");
        operar(tokenA, "BUY", "XPLG11", "FII", "10", "100", "0", "2025-03-03T10:00:00");
        operar(tokenA, "SELL", "XPLG11", "FII", "10", "130", "0", "2025-03-20T10:00:00");
        // Abril: ETF -500 não abate o lucro de ações do próprio mês (só dos seguintes): ações +1.000 -> 150,00
        operar(tokenA, "BUY", "ITUB4", "STOCK", "1000", "25", "0", "2025-04-01T10:00:00");
        operar(tokenA, "SELL", "ITUB4", "STOCK", "1000", "26", "0", "2025-04-15T10:00:00");
        operar(tokenA, "BUY", "BOVA11", "ETF", "50", "100", "0", "2025-04-01T10:00:00");
        operar(tokenA, "SELL", "BOVA11", "ETF", "50", "90", "0", "2025-04-15T10:00:00");
        // Maio/junho: prejuízo de cripto não compensa: SOL -1.000 em maio, BTC +30.000 em junho -> 4.500,00
        operar(tokenA, "BUY", "SOL", "CRYPTO", "10", "1000", "0", "2025-05-02T10:00:00");
        operar(tokenA, "SELL", "SOL", "CRYPTO", "10", "900", "0", "2025-05-20T10:00:00");
        operar(tokenA, "BUY", "BTC", "CRYPTO", "1", "10000", "0", "2025-06-02T10:00:00");
        operar(tokenA, "SELL", "BTC", "CRYPTO", "1", "40000", "0", "2025-06-20T10:00:00");

        var resultado = resultados(tokenA, 2025);

        assertDecimal("-10000.00", categoria(resultado, 1, "STOCK").get("lucroBruto"));
        assertDecimal("0.00", categoria(resultado, 1, "STOCK").get("impostoEstimado"));

        var bdrFev = categoria(resultado, 2, "BDR");
        assertDecimal("3000.00", bdrFev.get("prejuizoCompensado"));
        assertDecimal("0.00", bdrFev.get("baseCalculo"));
        assertDecimal("0.00", bdrFev.get("impostoEstimado"));

        var acoesMar = categoria(resultado, 3, "STOCK");
        assertDecimal("10000.00", acoesMar.get("lucroBruto"));
        assertDecimal("7000.00", acoesMar.get("prejuizoCompensado"));
        assertDecimal("3000.00", acoesMar.get("baseCalculo"));
        assertDecimal("450.00", acoesMar.get("impostoEstimado"));
        var fiiMar = categoria(resultado, 3, "FII");
        assertDecimal("100.00", fiiMar.get("prejuizoCompensado"));
        assertDecimal("200.00", fiiMar.get("baseCalculo"));
        assertDecimal("40.00", fiiMar.get("impostoEstimado"));

        var acoesAbr = categoria(resultado, 4, "STOCK");
        assertDecimal("0.00", acoesAbr.get("prejuizoCompensado"));
        assertDecimal("150.00", acoesAbr.get("impostoEstimado"));

        var criptoJun = categoria(resultado, 6, "CRYPTO");
        assertDecimal("0.00", criptoJun.get("prejuizoCompensado"));
        assertDecimal("4500.00", criptoJun.get("impostoEstimado"));

        // O ETF de abril ficou como saldo para o futuro; FII zerou
        assertDecimal("0.00", resultado.at("/prejuizoAcumulado/inicioDoAno/acoesEtfBdr"));
        assertDecimal("500.00", resultado.at("/prejuizoAcumulado/fimDoAno/acoesEtfBdr"));
        assertDecimal("0.00", resultado.at("/prejuizoAcumulado/fimDoAno/fii"));
        assertDecimal("5140.00", resultado.at("/totalAno/impostoEstimado"));

        // O saldo atravessa o ano
        var ano2026 = resultados(tokenA, 2026);
        assertDecimal("500.00", ano2026.at("/prejuizoAcumulado/inicioDoAno/acoesEtfBdr"));
        assertDecimal("0.00", ano2026.at("/totalAno/impostoEstimado"));
    }

    // --- Isolamento, alertas e formato ---

    @Test
    void cadaUsuarioSoVeOsProprios() throws Exception {
        operar(tokenA, "BUY", "PETR4", "STOCK", "2000", "10", "0", "2025-03-03T10:00:00");
        operar(tokenA, "SELL", "PETR4", "STOCK", "2000", "15", "0", "2025-03-20T10:00:00"); // 30 mil, lucro 10 mil
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");
        operar(tokenB, "BUY", "PETR4", "STOCK", "10", "10", "0", "2025-03-03T10:00:00");
        operar(tokenB, "SELL", "PETR4", "STOCK", "10", "11", "0", "2025-03-20T10:00:00");   // 110, isento

        var deB = resultados(tokenB, 2025);
        assertDecimal("110.00", categoria(deB, 3, "STOCK").get("totalVendas"));
        assertDecimal("0.00", deB.at("/totalAno/impostoEstimado"));
        assertDecimal("1500.00", resultados(tokenA, 2025).at("/totalAno/impostoEstimado"));

        var limiteB = json(mockMvc.perform(get("/api/resultados/limite-isencao").param("mes", "2025-03").header("Authorization", "Bearer " + tokenB)));
        assertDecimal("110.00", limiteB.at("/acoes/vendido"));
    }

    @Test
    void alertaDePossivelDayTradeEDeDarfAbaixoDoMinimo() throws Exception {
        // Compra e venda de PETR4 no mesmo dia (horários diferentes) -> alerta, sem cálculo próprio
        operar(tokenA, "BUY", "PETR4", "STOCK", "100", "10", "0", "2025-03-10T10:00:00");
        operar(tokenA, "SELL", "PETR4", "STOCK", "100", "11", "0", "2025-03-10T15:00:00");
        // Compra e venda em dias diferentes -> sem alerta
        operar(tokenA, "BUY", "VALE3", "STOCK", "100", "10", "0", "2025-03-11T10:00:00");
        operar(tokenA, "SELL", "VALE3", "STOCK", "100", "11", "0", "2025-03-12T10:00:00");
        // FII com lucro de 20 -> imposto de 4,00 -> DARF abaixo de R$ 10
        operar(tokenA, "BUY", "HGLG11", "FII", "10", "100", "0", "2025-08-01T10:00:00");
        operar(tokenA, "SELL", "HGLG11", "FII", "10", "102", "0", "2025-08-15T10:00:00");

        mockMvc.perform(get("/api/resultados").param("ano", "2025").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertas", hasSize(2)))
                .andExpect(jsonPath("$.alertas[0].tipo").value("POSSIVEL_DAY_TRADE"))
                .andExpect(jsonPath("$.alertas[0].assetSymbol").value("PETR4"))
                .andExpect(jsonPath("$.alertas[0].data").value("2025-03-10"))
                .andExpect(jsonPath("$.alertas[0].mes").value("2025-03"))
                .andExpect(jsonPath("$.alertas[1].tipo").value("DARF_ABAIXO_DO_MINIMO"))
                .andExpect(jsonPath("$.alertas[1].mes").value("2025-08"));
    }

    @Test
    void sempreDozeMesesSoComAsCategoriasVendidas() throws Exception {
        operar(tokenA, "BUY", "PETR4", "STOCK", "10", "10", "0", "2025-03-03T10:00:00");
        operar(tokenA, "SELL", "PETR4", "STOCK", "10", "11", "0", "2025-03-20T10:00:00");

        mockMvc.perform(get("/api/resultados").param("ano", "2025").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ano").value(2025))
                .andExpect(jsonPath("$.meses", hasSize(12)))
                .andExpect(jsonPath("$.meses[0].mes").value("2025-01"))
                .andExpect(jsonPath("$.meses[0].categorias", hasSize(0)))
                .andExpect(jsonPath("$.meses[2].categorias", hasSize(1)))
                .andExpect(jsonPath("$.meses[2].categorias[0].categoria").value("STOCK"))
                .andExpect(jsonPath("$.meses[2].darfs", hasSize(0)));
    }

    // --- Limite de isenção ---

    @Test
    void limiteDeIsencaoMostraVendidoEDisponivelEmAcoesECripto() throws Exception {
        operar(tokenA, "BUY", "PETR4", "STOCK", "1000", "10", "0", "2025-03-03T10:00:00");
        operar(tokenA, "SELL", "PETR4", "STOCK", "1000", "15", "3", "2025-03-10T10:00:00"); // 15.000 (a taxa não conta)
        operar(tokenA, "BUY", "BOVA11", "ETF", "100", "100", "0", "2025-03-03T10:00:00");
        operar(tokenA, "SELL", "BOVA11", "ETF", "100", "110", "0", "2025-03-10T10:00:00"); // ETF não conta nas ações
        operar(tokenA, "BUY", "BTC", "CRYPTO", "1", "1000", "0", "2025-03-03T10:00:00");
        operar(tokenA, "SELL", "BTC", "CRYPTO", "0.5", "10000", "0", "2025-03-10T10:00:00"); // 5.000

        var marco = json(mockMvc.perform(get("/api/resultados/limite-isencao").param("mes", "2025-03").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value("2025-03")));
        assertDecimal("20000.00", marco.at("/acoes/limite"));
        assertDecimal("15000.00", marco.at("/acoes/vendido"));
        assertDecimal("5000.00", marco.at("/acoes/disponivel"));
        assertTrue(marco.at("/acoes/dentroDoLimite").asBoolean());
        assertDecimal("35000.00", marco.at("/cripto/limite"));
        assertDecimal("5000.00", marco.at("/cripto/vendido"));
        assertDecimal("30000.00", marco.at("/cripto/disponivel"));

        // Abril: passou do limite de ações -> disponível zero
        operar(tokenA, "BUY", "VALE3", "STOCK", "1000", "10", "0", "2025-04-01T10:00:00");
        operar(tokenA, "SELL", "VALE3", "STOCK", "1000", "25", "0", "2025-04-10T10:00:00");
        var abril = json(mockMvc.perform(get("/api/resultados/limite-isencao").param("mes", "2025-04").header("Authorization", "Bearer " + tokenA)));
        assertDecimal("25000.00", abril.at("/acoes/vendido"));
        assertDecimal("0.00", abril.at("/acoes/disponivel"));
        assertFalse(abril.at("/acoes/dentroDoLimite").asBoolean());
        assertDecimal("0.00", abril.at("/cripto/vendido"));
    }

    @Test
    void parametrosInvalidosOuSemTokenSaoRecusados() throws Exception {
        mockMvc.perform(get("/api/resultados").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("ano"));
        mockMvc.perform(get("/api/resultados").param("ano", "1800").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("ano"));
        mockMvc.perform(get("/api/resultados/limite-isencao").param("mes", "2025-13").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("mes"));
        mockMvc.perform(get("/api/resultados/limite-isencao").param("mes", "março").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("mes"));
        mockMvc.perform(get("/api/resultados").param("ano", "2025"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private JsonNode operar(String token, String tipo, String simbolo, String classe, String quantidade, String preco,
                            String taxa, String data) throws Exception {
        var corpo = new HashMap<String, Object>();
        corpo.put("type", tipo);
        corpo.put("assetSymbol", simbolo);
        corpo.put("assetClass", classe);
        corpo.put("quantity", new BigDecimal(quantidade));
        corpo.put("price", new BigDecimal(preco));
        corpo.put("fee", new BigDecimal(taxa));
        corpo.put("transactionDate", data);
        corpo.put("broker", "TESTE");
        return json(mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated()));
    }

    private JsonNode resultados(String token, int ano) throws Exception {
        return json(mockMvc.perform(get("/api/resultados").param("ano", String.valueOf(ano)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
    }

    // Categoria do mês (1 a 12) no resultado do ano
    private static JsonNode categoria(JsonNode resultado, int mes, String categoria) {
        return StreamSupport.stream(resultado.at("/meses/" + (mes - 1) + "/categorias").spliterator(), false)
                .filter(c -> c.get("categoria").asString().equals(categoria))
                .findFirst()
                .orElseThrow(() -> new AssertionError("sem " + categoria + " no mês " + mes + ": " + resultado.at("/meses/" + (mes - 1))));
    }

    private JsonNode json(ResultActions resultado) throws Exception {
        return jsonMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private static void assertDecimal(String esperado, JsonNode valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor.decimalValue()), "esperado " + esperado + ", veio " + valor);
    }
}
