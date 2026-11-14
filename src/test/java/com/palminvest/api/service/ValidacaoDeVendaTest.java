package com.palminvest.api.service;

import com.palminvest.api.IntegracaoTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Regra: em nenhum ponto da linha do tempo de um ativo a quantidade em carteira pode ficar negativa
class ValidacaoDeVendaTest extends IntegracaoTestBase {

    private static final String ROTA = "/api/transactions";
    private static final String JAN = "2025-01-10T10:00:00";
    private static final String MAR = "2025-03-10T10:00:00";
    private static final String JUN = "2025-06-10T10:00:00";

    // --- POST ---

    @Test
    void vendaSemCompraRetorna422() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");

        movimento(token, "SELL", "PETR4", "10", MAR)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value(containsString("Saldo insuficiente de PETR4")))
                .andExpect(jsonPath("$.mensagem").value(containsString("disponível 0, venda de 10")));

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void vendaMaiorQueOSaldoRetorna422EVendaIgualAoSaldoPassa() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        movimento(token, "BUY", "PETR4", "10", JAN).andExpect(status().isCreated());

        movimento(token, "SELL", "PETR4", "10.00000001", MAR)
                .andExpect(status().isUnprocessableContent());
        movimento(token, "SELL", "PETR4", "10", MAR)
                .andExpect(status().isCreated());
        // Agora o saldo é zero: nem 1 unidade a mais
        movimento(token, "SELL", "PETR4", "1", JUN)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void vendaComDataAnteriorACompraRetorna422() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        movimento(token, "BUY", "PETR4", "10", MAR).andExpect(status().isCreated());

        // Em janeiro ainda não havia PETR4 na carteira
        movimento(token, "SELL", "PETR4", "5", JAN)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value(containsString("10/01/2025 10:00")));
    }

    @Test
    void compraEVendaNoMesmoInstanteSaoPermitidas() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        movimento(token, "BUY", "PETR4", "10", MAR).andExpect(status().isCreated());

        movimento(token, "SELL", "PETR4", "10", MAR).andExpect(status().isCreated());
    }

    @Test
    void saldoDeOutroUsuarioNaoConta() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");
        movimento(tokenB, "BUY", "PETR4", "100", JAN).andExpect(status().isCreated());

        movimento(tokenA, "SELL", "PETR4", "1", MAR)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void saldoEhPorSimbolo() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        movimento(token, "BUY", "VALE3", "10", JAN).andExpect(status().isCreated());

        movimento(token, "SELL", "PETR4", "1", MAR)
                .andExpect(status().isUnprocessableContent());
    }

    // --- PUT ---

    @Test
    void editarCompraParaMenosDoQueAsVendasPosterioresRetorna422() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        long idCompra = idDe(movimento(token, "BUY", "PETR4", "10", JAN));
        movimento(token, "SELL", "PETR4", "8", MAR).andExpect(status().isCreated());

        // Reduzir a compra para 5 deixaria a venda de 8 descoberta
        editar(token, idCompra, "BUY", "PETR4", "5", JAN)
                .andExpect(status().isUnprocessableContent());
        // Reduzir para 8 ainda cobre
        editar(token, idCompra, "BUY", "PETR4", "8", JAN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8));
    }

    @Test
    void moverCompraParaDepoisDaVendaRetorna422() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        long idCompra = idDe(movimento(token, "BUY", "PETR4", "10", JAN));
        movimento(token, "SELL", "PETR4", "10", MAR).andExpect(status().isCreated());

        editar(token, idCompra, "BUY", "PETR4", "10", JUN)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void editarAPropriaVendaNaoContaDuasVezes() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        movimento(token, "BUY", "PETR4", "10", JAN).andExpect(status().isCreated());
        long idVenda = idDe(movimento(token, "SELL", "PETR4", "6", MAR));

        // Se a versão antiga (6) continuasse na linha do tempo, 6 + 10 > 10 falharia
        editar(token, idVenda, "SELL", "PETR4", "10", MAR)
                .andExpect(status().isOk());
        editar(token, idVenda, "SELL", "PETR4", "11", MAR)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void trocarCompraDeSimboloRetorna422SeOSimboloAntigoFicaDescoberto() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        long idCompra = idDe(movimento(token, "BUY", "PETR4", "10", JAN));
        movimento(token, "SELL", "PETR4", "10", MAR).andExpect(status().isCreated());

        // A compra vira VALE3: PETR4 perde a cobertura da venda
        editar(token, idCompra, "BUY", "VALE3", "10", JAN)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value(containsString("PETR4")));
    }

    @Test
    void trocarVendaDeSimboloRetorna422SeONovoSimboloNaoTemSaldo() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        movimento(token, "BUY", "PETR4", "10", JAN).andExpect(status().isCreated());
        long idVenda = idDe(movimento(token, "SELL", "PETR4", "10", MAR));

        editar(token, idVenda, "SELL", "VALE3", "10", MAR)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value(containsString("VALE3")));
    }

    // --- DELETE ---

    @Test
    void excluirCompraQueSustentaVendaRetorna422EMantemATransacao() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        long idCompra = idDe(movimento(token, "BUY", "PETR4", "10", JAN));
        long idVenda = idDe(movimento(token, "SELL", "PETR4", "10", MAR));

        mockMvc.perform(delete(ROTA + "/" + idCompra).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent());
        assertTrue(transactionRepository.findById(idCompra).isPresent());

        // Excluindo a venda primeiro, a compra pode sair
        mockMvc.perform(delete(ROTA + "/" + idVenda).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ROTA + "/" + idCompra).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void excluirCompraComSobraDeSaldoPassa() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");
        movimento(token, "BUY", "PETR4", "10", JAN).andExpect(status().isCreated());
        long idSegundaCompra = idDe(movimento(token, "BUY", "PETR4", "5", MAR));
        movimento(token, "SELL", "PETR4", "10", JUN).andExpect(status().isCreated());

        mockMvc.perform(delete(ROTA + "/" + idSegundaCompra).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // --- helpers ---

    private Map<String, Object> corpo(String type, String symbol, String quantity, String data) {
        var corpo = new HashMap<>(transacaoValida(new BigDecimal(quantity)));
        corpo.put("type", type);
        corpo.put("assetSymbol", symbol);
        corpo.put("transactionDate", data);
        return corpo;
    }

    private ResultActions movimento(String token, String type, String symbol, String quantity, String data) throws Exception {
        return mockMvc.perform(post(ROTA)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(corpo(type, symbol, quantity, data))));
    }

    private ResultActions editar(String token, long id, String type, String symbol, String quantity, String data) throws Exception {
        return mockMvc.perform(put(ROTA + "/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(corpo(type, symbol, quantity, data))));
    }

    private long idDe(ResultActions resultado) throws Exception {
        var json = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("id").asLong();
    }
}
