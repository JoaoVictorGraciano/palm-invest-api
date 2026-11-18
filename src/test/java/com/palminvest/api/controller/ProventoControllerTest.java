package com.palminvest.api.controller;

import com.palminvest.api.IntegracaoTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /api/proventos: CRUD do dono, validações, ativo que o usuário tinha na data, resumo anual e exclusão do ativo
class ProventoControllerTest extends IntegracaoTestBase {

    private static final String ROTA = "/api/proventos";

    private String tokenA;

    @BeforeEach
    void prepararCarteira() throws Exception {
        tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        comprar(tokenA, "PETR4", "STOCK", "2025-01-10T15:00:00");
        comprar(tokenA, "HGLG11", "FII", "2024-06-03T11:00:00");
    }

    // --- CRUD ---

    @Test
    void criaListaEditaEApagaUmProvento() throws Exception {
        var resposta = registrar(tokenA, provento(" petr4 ", "DIVIDENDO", "2025-03-15", "100.00", "0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.assetSymbol").value("PETR4"))
                .andExpect(jsonPath("$.assetClass").value("STOCK"))
                .andExpect(jsonPath("$.tipo").value("DIVIDENDO"))
                .andExpect(jsonPath("$.dataPagamento").value("2025-03-15"))
                .andExpect(jsonPath("$.valorBruto").value(100.0))
                .andExpect(jsonPath("$.irRetido").value(0.0))
                .andExpect(jsonPath("$.valorLiquido").value(100.0))
                .andReturn().getResponse().getContentAsString();
        long id = jsonMapper.readTree(resposta).get("id").asLong();

        listar(tokenA, "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        // JCP com IR retido: líquido = bruto - IR
        var json = editar(tokenA, id, provento("PETR4", "JCP", "2025-03-20", "50.00", "7.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.tipo").value("JCP"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, new BigDecimal("42.50").compareTo(jsonMapper.readTree(json).get("valorLiquido").decimalValue()));
        var noBanco = proventoRepository.findById(id).orElseThrow();
        assertEquals(0, new BigDecimal("7.50").compareTo(noBanco.getIrRetido()));

        mockMvc.perform(delete(ROTA + "/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        listar(tokenA, "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void classeVemDaTransacaoENaoDoCorpo() throws Exception {
        var corpo = provento("HGLG11", "RENDIMENTO", "2025-02-14", "80.00", null);
        corpo.put("assetClass", "STOCK"); // ignorado: HGLG11 foi registrado como FII

        registrar(tokenA, corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetClass").value("FII"))
                // Sem IR informado, vale zero
                .andExpect(jsonPath("$.irRetido").value(0.0))
                .andExpect(jsonPath("$.valorLiquido").value(80.0));
    }

    @Test
    void getFiltraPorAnoEAtivoEOrdenaDoPagamentoMaisRecente() throws Exception {
        long rendimento2024 = idDe(registrar(tokenA, provento("HGLG11", "RENDIMENTO", "2024-12-13", "75.00", "0")));
        long rendimentoFev = idDe(registrar(tokenA, provento("HGLG11", "RENDIMENTO", "2025-02-14", "80.00", "0")));
        long dividendoMar = idDe(registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-03-15", "100.00", "0")));
        long jcpMar = idDe(registrar(tokenA, provento("PETR4", "JCP", "2025-03-15", "40.00", "6.00")));

        listar(tokenA, "")
                .andExpect(jsonPath("$[*].id").value(contains((int) jcpMar, (int) dividendoMar, (int) rendimentoFev, (int) rendimento2024)));
        listar(tokenA, "?ano=2025")
                .andExpect(jsonPath("$[*].id").value(contains((int) jcpMar, (int) dividendoMar, (int) rendimentoFev)));
        listar(tokenA, "?assetSymbol=hglg11")
                .andExpect(jsonPath("$[*].id").value(contains((int) rendimentoFev, (int) rendimento2024)));
        listar(tokenA, "?ano=2024&assetSymbol=HGLG11")
                .andExpect(jsonPath("$[*].id").value(contains((int) rendimento2024)));
        listar(tokenA, "?ano=2023").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anoInvalidoNoFiltroRetorna400() throws Exception {
        listar(tokenA, "?ano=1800").andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("ano"));
        listar(tokenA, "?ano=abc").andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("ano"));
    }

    // --- Isolamento entre usuários ---

    @Test
    void usuarioBNaoVeNemAlteraProventoDeA() throws Exception {
        long idDeA = idDe(registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-03-15", "100.00", "0")));
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");

        listar(tokenB, "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        editar(tokenB, idDeA, provento("PETR4", "DIVIDENDO", "2025-03-15", "999.00", "0")).andExpect(status().isNotFound());
        mockMvc.perform(delete(ROTA + "/" + idDeA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ROTA + "/resumo").param("ano", "2025").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.totalLiquido").value(0.0));

        var noBanco = proventoRepository.findById(idDeA).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(noBanco.getValorBruto()));
    }

    @Test
    void transacaoDeOutroUsuarioNaoValeComoAtivoNaCarteira() throws Exception {
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");

        // PETR4 está na carteira de A, não na de B
        registrar(tokenB, provento("PETR4", "DIVIDENDO", "2025-03-15", "100.00", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value(containsString("PETR4")));
        assertEquals(0, proventoRepository.count());
    }

    // --- Validações ---

    @Test
    void valorBrutoPrecisaSerPositivoComAteDuasCasas() throws Exception {
        registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-03-15", "0", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[*].campo", hasItem("valorBruto")));
        registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-03-15", "-10.00", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[*].campo", hasItem("valorBruto")));
        registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-03-15", "10.005", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[*].campo", hasItem("valorBruto")));
        var semValor = provento("PETR4", "DIVIDENDO", "2025-03-15", "10.00", "0");
        semValor.remove("valorBruto");
        registrar(tokenA, semValor).andExpect(status().isBadRequest()).andExpect(jsonPath("$[*].campo", hasItem("valorBruto")));
        assertEquals(0, proventoRepository.count());
    }

    @Test
    void irRetidoEntreZeroEOValorBruto() throws Exception {
        registrar(tokenA, provento("PETR4", "JCP", "2025-03-15", "100.00", "100.01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("irRetido"))
                .andExpect(jsonPath("$[0].mensagem").value("não pode ser maior que o valor bruto"));
        registrar(tokenA, provento("PETR4", "JCP", "2025-03-15", "100.00", "-1.00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[*].campo", hasItem("irRetido")));
        assertEquals(0, proventoRepository.count());

        // IR igual ao bruto é o limite aceito (líquido zero)
        registrar(tokenA, provento("PETR4", "JCP", "2025-03-15", "100.00", "100.00"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.valorLiquido").value(0.0));
    }

    @Test
    void ativoQueOUsuarioNuncaTeveRetorna422() throws Exception {
        registrar(tokenA, provento("VALE3", "DIVIDENDO", "2025-03-15", "50.00", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value("Você não tem transação de VALE3 até 15/03/2025: registre a compra do ativo antes do provento"));
        assertEquals(0, proventoRepository.count());
    }

    @Test
    void pagamentoAntesDaPrimeiraCompraRetorna422ENoMesmoDiaPassa() throws Exception {
        // PETR4 foi comprada em 10/01/2025 às 15h
        registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-01-09", "50.00", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value(containsString("09/01/2025")));
        registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-01-10", "50.00", "0"))
                .andExpect(status().isCreated());
    }

    @Test
    void putTambemValidaOAtivoNaDataEMantemOProventoSeFalhar() throws Exception {
        long id = idDe(registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-03-15", "100.00", "0")));

        editar(tokenA, id, provento("VALE3", "DIVIDENDO", "2025-03-15", "100.00", "0"))
                .andExpect(status().isUnprocessableContent());
        editar(tokenA, id, provento("PETR4", "DIVIDENDO", "2024-12-01", "100.00", "0"))
                .andExpect(status().isUnprocessableContent());

        var noBanco = proventoRepository.findById(id).orElseThrow();
        assertEquals("PETR4", noBanco.getAssetSymbol());
        assertEquals(LocalDate.of(2025, 3, 15), noBanco.getDataPagamento());
    }

    @Test
    void tipoForaDoEnumEDataFuturaRetornam400() throws Exception {
        registrar(tokenA, provento("PETR4", "BONIFICACAO", "2025-03-15", "10.00", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("tipo"))
                .andExpect(jsonPath("$[0].mensagem").value(containsString("DIVIDENDO, JCP, RENDIMENTO, AMORTIZACAO")));
        registrar(tokenA, provento("PETR4", "DIVIDENDO", LocalDate.now().plusDays(1).toString(), "10.00", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[*].campo", hasItem("dataPagamento")));
    }

    // --- Resumo ---

    @Test
    void resumoDoAnoSomaOLiquidoPorMesAtivoETipo() throws Exception {
        registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-01-20", "100.00", "0")).andExpect(status().isCreated());
        registrar(tokenA, provento("PETR4", "JCP", "2025-03-10", "200.00", "30.00")).andExpect(status().isCreated());
        registrar(tokenA, provento("HGLG11", "RENDIMENTO", "2025-03-14", "80.00", "0")).andExpect(status().isCreated());
        registrar(tokenA, provento("HGLG11", "RENDIMENTO", "2024-12-13", "75.00", "0")).andExpect(status().isCreated()); // outro ano
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");
        comprar(tokenB, "PETR4", "STOCK", "2025-01-02T10:00:00");
        registrar(tokenB, provento("PETR4", "DIVIDENDO", "2025-03-15", "999.00", "0")).andExpect(status().isCreated()); // outro usuário

        var json = jsonMapper.readTree(mockMvc.perform(get(ROTA + "/resumo").param("ano", "2025").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ano").value(2025))
                .andExpect(jsonPath("$.porMes", hasSize(12)))
                .andExpect(jsonPath("$.porMes[*].mes").value(contains(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)))
                .andExpect(jsonPath("$.porAtivo[*].assetSymbol").value(contains("PETR4", "HGLG11")))
                .andExpect(jsonPath("$.porAtivo[0].assetClass").value("STOCK"))
                .andExpect(jsonPath("$.porAtivo[1].assetClass").value("FII"))
                .andExpect(jsonPath("$.porTipo[*].tipo").value(contains("DIVIDENDO", "JCP", "RENDIMENTO", "AMORTIZACAO")))
                .andReturn().getResponse().getContentAsString());

        assertDecimal("380.00", json.at("/totalBruto"));
        assertDecimal("30.00", json.at("/totalIrRetido"));
        assertDecimal("350.00", json.at("/totalLiquido"));
        assertDecimal("100.00", json.at("/porMes/0/totalLiquido"));
        assertDecimal("0.00", json.at("/porMes/1/totalLiquido"));
        assertDecimal("250.00", json.at("/porMes/2/totalLiquido"));
        assertDecimal("0.00", json.at("/porMes/11/totalLiquido"));
        assertDecimal("270.00", json.at("/porAtivo/0/totalLiquido"));
        assertDecimal("80.00", json.at("/porAtivo/1/totalLiquido"));
        assertDecimal("100.00", json.at("/porTipo/0/totalLiquido"));
        assertDecimal("170.00", json.at("/porTipo/1/totalLiquido"));
        assertDecimal("80.00", json.at("/porTipo/2/totalLiquido"));
        assertDecimal("0.00", json.at("/porTipo/3/totalLiquido"));
    }

    @Test
    void resumoSemProventosTemZerosEResumoSemAnoRetorna400() throws Exception {
        mockMvc.perform(get(ROTA + "/resumo").param("ano", "2030").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLiquido").value(0.0))
                .andExpect(jsonPath("$.porMes", hasSize(12)))
                .andExpect(jsonPath("$.porAtivo", hasSize(0)))
                .andExpect(jsonPath("$.porTipo", hasSize(4)));
        mockMvc.perform(get(ROTA + "/resumo").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("ano"));
    }

    // --- Exclusão do ativo ---

    @Test
    void excluirOAtivoApagaOsProventosDeleSoDoUsuario() throws Exception {
        registrar(tokenA, provento("PETR4", "DIVIDENDO", "2025-01-20", "100.00", "0")).andExpect(status().isCreated());
        registrar(tokenA, provento("PETR4", "JCP", "2025-03-10", "200.00", "30.00")).andExpect(status().isCreated());
        long rendimento = idDe(registrar(tokenA, provento("HGLG11", "RENDIMENTO", "2025-03-14", "80.00", "0")));
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");
        comprar(tokenB, "PETR4", "STOCK", "2025-01-02T10:00:00");
        long proventoDeB = idDe(registrar(tokenB, provento("PETR4", "DIVIDENDO", "2025-03-15", "999.00", "0")));

        mockMvc.perform(delete("/api/transactions").param("assetSymbol", "petr4").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetSymbol").value("PETR4"))
                .andExpect(jsonPath("$.excluidas").value(1))
                .andExpect(jsonPath("$.proventosExcluidos").value(2));

        listar(tokenA, "").andExpect(jsonPath("$[*].id").value(contains((int) rendimento)));
        assertTrue(proventoRepository.findById(proventoDeB).isPresent());
    }

    @Test
    void excluirAtivoSoComProventosRestantesApagaOsProventos() throws Exception {
        long idCompra = jsonMapper.readTree(comprar(tokenA, "VALE3", "STOCK", "2025-02-03T10:00:00")).get("id").asLong();
        registrar(tokenA, provento("VALE3", "DIVIDENDO", "2025-03-15", "30.00", "0")).andExpect(status().isCreated());
        // A compra é apagada sozinha; o provento fica (o histórico de proventos não é desfeito por isso)
        mockMvc.perform(delete("/api/transactions/" + idCompra).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/transactions").param("assetSymbol", "VALE3").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excluidas").value(0))
                .andExpect(jsonPath("$.proventosExcluidos").value(1));
        mockMvc.perform(delete("/api/transactions").param("assetSymbol", "VALE3").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private String comprar(String token, String simbolo, String classe, String data) throws Exception {
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("10")));
        corpo.put("assetSymbol", simbolo);
        corpo.put("assetClass", classe);
        corpo.put("transactionDate", data);
        return mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static Map<String, Object> provento(String simbolo, String tipo, String data, String bruto, String ir) {
        var corpo = new HashMap<String, Object>();
        corpo.put("assetSymbol", simbolo);
        corpo.put("tipo", tipo);
        corpo.put("dataPagamento", data);
        corpo.put("valorBruto", new BigDecimal(bruto));
        if (ir != null) {
            corpo.put("irRetido", new BigDecimal(ir));
        }
        corpo.put("observacao", "teste");
        return corpo;
    }

    private ResultActions registrar(String token, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post(ROTA)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(corpo)));
    }

    private ResultActions editar(String token, long id, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put(ROTA + "/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(corpo)));
    }

    private ResultActions listar(String token, String query) throws Exception {
        return mockMvc.perform(get(ROTA + query).header("Authorization", "Bearer " + token));
    }

    private long idDe(ResultActions resultado) throws Exception {
        var json = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("id").asLong();
    }

    private static void assertDecimal(String esperado, tools.jackson.databind.JsonNode valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor.decimalValue()), "esperado " + esperado + ", veio " + valor);
    }
}
