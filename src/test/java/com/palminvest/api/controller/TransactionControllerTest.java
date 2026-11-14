package com.palminvest.api.controller;

import com.palminvest.api.IntegracaoTestBase;
import com.palminvest.api.model.AssetClass;
import com.palminvest.api.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerTest extends IntegracaoTestBase {

    private static final String ROTA = "/api/transactions";

    // --- POST: id e dono ---

    @Test
    void postIgnoraIdDoCorpoEDefineDonoPeloToken() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("10")));
        corpo.put("id", 999);

        var resposta = postTransacao(tokenA, corpo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = jsonMapper.readTree(resposta).get("id").asLong();

        assertNotEquals(999L, id);
        var salva = transactionRepository.findById(id).orElseThrow();
        assertEquals(idDoUsuario("a@exemplo.com"), salva.getUsuario().getId());
    }

    @Test
    void postNormalizaAssetSymbolParaMaiusculasSemEspacos() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("10")));
        corpo.put("assetSymbol", "  petr4 ");

        var resposta = postTransacao(tokenA, corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetSymbol").value("PETR4"))
                .andReturn().getResponse().getContentAsString();

        var salva = transactionRepository.findById(jsonMapper.readTree(resposta).get("id").asLong()).orElseThrow();
        assertEquals("PETR4", salva.getAssetSymbol());
    }

    // --- type ---

    @Test
    void typeForaDoEnumRetorna400ComValoresAceitos() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("10")));
        corpo.put("type", "COMPRA");

        postTransacao(tokenA, corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("type"))
                .andExpect(jsonPath("$[0].mensagem").value(containsString("BUY, SELL")));

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void typeSellEhAceitoEDevolvido() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        criarTransacao(tokenA, new BigDecimal("10")); // compra que cobre a venda
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("10")));
        corpo.put("type", "SELL");

        postTransacao(tokenA, corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SELL"));
    }

    // --- assetClass ---

    @Test
    void postSemAssetClassRetorna400() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("10")));
        corpo.remove("assetClass");

        postTransacao(tokenA, corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[*].campo", hasItem("assetClass")));

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void assetClassForaDoEnumRetorna400ComValoresAceitos() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("10")));
        corpo.put("assetClass", "ACAO");

        postTransacao(tokenA, corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("assetClass"))
                .andExpect(jsonPath("$[0].mensagem").value(containsString("STOCK, CRYPTO, FII, ETF, BDR")));
    }

    @Test
    void assetClassAceitaFiiEtfEBdr() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var exemplos = Map.of("FII", "HGLG11", "ETF", "BOVA11", "BDR", "AAPL34");

        // Passa pelo enum, pela coluna e pelo CHECK do banco (a migração V5 precisa ter ampliado a lista)
        for (var exemplo : exemplos.entrySet()) {
            var corpo = new HashMap<>(transacaoValida(new BigDecimal("1")));
            corpo.put("assetClass", exemplo.getKey());
            corpo.put("assetSymbol", exemplo.getValue());

            var resposta = postTransacao(tokenA, corpo)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.assetClass").value(exemplo.getKey()))
                    .andReturn().getResponse().getContentAsString();
            long id = jsonMapper.readTree(resposta).get("id").asLong();
            assertEquals(AssetClass.valueOf(exemplo.getKey()), transactionRepository.findById(id).orElseThrow().getAssetClass());
        }
        assertEquals(3, transactionRepository.count());
    }

    @Test
    void assetClassEhPersistidaEDevolvidaNoPostPutEGet() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("0.5")));
        corpo.put("assetSymbol", "BTC");
        corpo.put("assetClass", "CRYPTO");

        var resposta = postTransacao(tokenA, corpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetClass").value("CRYPTO"))
                .andReturn().getResponse().getContentAsString();
        long id = jsonMapper.readTree(resposta).get("id").asLong();

        mockMvc.perform(get(ROTA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assetClass").value("CRYPTO"));

        // PUT troca a classe (corrigindo um cadastro errado) e o banco reflete
        corpo.put("assetClass", "STOCK");
        putTransacao(tokenA, id, corpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetClass").value("STOCK"));
        assertEquals(AssetClass.STOCK, transactionRepository.findById(id).orElseThrow().getAssetClass());
    }

    // --- quantity ---

    @Test
    void quantityFracionadaComOitoCasasEhSalvaSemPerda() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var quantity = new BigDecimal("0.12345678");

        var resposta = postTransacao(tokenA, transacaoValida(quantity))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var json = jsonMapper.readTree(resposta);

        // Compara como BigDecimal (compareTo), sem passar por double
        assertEquals(0, quantity.compareTo(json.get("quantity").decimalValue()));
        var salva = transactionRepository.findById(json.get("id").asLong()).orElseThrow();
        assertEquals(0, quantity.compareTo(salva.getQuantity()));
    }

    @Test
    void quantityComNoveCasasRetorna400() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");

        postTransacao(tokenA, transacaoValida(new BigDecimal("0.123456789")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[*].campo", hasItem("quantity")));

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void quantityZeroOuNegativaRetorna400() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");

        for (var quantity : new String[]{"0", "-1"}) {
            postTransacao(tokenA, transacaoValida(new BigDecimal(quantity)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$[*].campo", hasItem("quantity")));
        }

        assertEquals(0, transactionRepository.count());
    }

    // --- price ---

    @Test
    void priceComOitoCasasEhSalvoSemPerda() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var price = new BigDecimal("0.00001234");
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("10")));
        corpo.put("price", price);

        var resposta = postTransacao(tokenA, corpo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var json = jsonMapper.readTree(resposta);

        // Compara como BigDecimal (compareTo), sem passar por double
        assertEquals(0, price.compareTo(json.get("price").decimalValue()));
        var salva = transactionRepository.findById(json.get("id").asLong()).orElseThrow();
        assertEquals(0, price.compareTo(salva.getPrice()));
    }

    @Test
    void priceComNoveCasasRetorna400() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("10")));
        corpo.put("price", new BigDecimal("0.000012345"));

        postTransacao(tokenA, corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[*].campo", hasItem("price")));

        assertEquals(0, transactionRepository.count());
    }

    // --- ordenação ---

    @Test
    void getDevolveDaMaisRecenteParaAMaisAntiga() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        // Cria fora de ordem cronológica: meio, antiga, recente
        long idMeio = criarTransacaoNaData(tokenA, "2025-03-10T10:00:00");
        long idAntiga = criarTransacaoNaData(tokenA, "2025-01-05T10:00:00");
        long idRecente = criarTransacaoNaData(tokenA, "2025-06-20T10:00:00");

        mockMvc.perform(get(ROTA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(idRecente))
                .andExpect(jsonPath("$[1].id").value(idMeio))
                .andExpect(jsonPath("$[2].id").value(idAntiga));
    }

    private long criarTransacaoNaData(String token, String data) throws Exception {
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("1")));
        corpo.put("transactionDate", data);
        var resposta = postTransacao(token, corpo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(resposta).get("id").asLong();
    }

    // --- DELETE por ativo ---

    @Test
    void deleteDoAtivoApagaSoAsDoSimboloEDoUsuario() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");
        criarTransacao(tokenA, new BigDecimal("10"));                 // PETR4 de A
        criarTransacao(tokenA, new BigDecimal("5"));                  // PETR4 de A
        long idVale = criarTransacaoDoSimbolo(tokenA, "VALE3");       // outro ativo de A
        long idPetrDeB = criarTransacao(tokenB, new BigDecimal("1")); // PETR4 de B

        mockMvc.perform(delete(ROTA).param("assetSymbol", " petr4 ").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetSymbol").value("PETR4"))
                .andExpect(jsonPath("$.excluidas").value(2));

        assertEquals(2, transactionRepository.count());
        assertTrue(transactionRepository.findById(idVale).isPresent());
        assertTrue(transactionRepository.findById(idPetrDeB).isPresent());
    }

    @Test
    void deleteDoAtivoSemTransacoesRetorna404() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");
        criarTransacao(tokenB, new BigDecimal("1")); // PETR4 só de B

        mockMvc.perform(delete(ROTA).param("assetSymbol", "PETR4").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void deleteDoAtivoSemParametroOuEmBrancoRetorna400() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");

        mockMvc.perform(delete(ROTA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("assetSymbol"));
        mockMvc.perform(delete(ROTA).param("assetSymbol", "  ").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("assetSymbol"));
    }

    private long criarTransacaoDoSimbolo(String token, String simbolo) throws Exception {
        var corpo = new HashMap<>(transacaoValida(new BigDecimal("1")));
        corpo.put("assetSymbol", simbolo);
        var resposta = postTransacao(token, corpo)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(resposta).get("id").asLong();
    }

    // --- Isolamento entre usuários ---

    @Test
    void usuarioBNaoVeNemAlteraTransacaoDeA() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        var tokenB = novoUsuarioComToken("B", "b@exemplo.com");
        long idDeA = criarTransacao(tokenA, new BigDecimal("10"));

        // A vê a própria transação; B vê uma lista vazia
        mockMvc.perform(get(ROTA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(idDeA));
        mockMvc.perform(get(ROTA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // B tenta editar e apagar: 404, como se a transação não existisse
        var edicaoDeB = new HashMap<>(transacaoValida(new BigDecimal("99")));
        edicaoDeB.put("observation", "editada pelo B");
        putTransacao(tokenB, idDeA, edicaoDeB)
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ROTA + "/" + idDeA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // A transação de A continua intacta no banco
        var noBanco = transactionRepository.findById(idDeA).orElseThrow();
        assertEquals("criada no teste", noBanco.getObservation());
        assertEquals(0, new BigDecimal("10").compareTo(noBanco.getQuantity()));
        assertEquals(idDoUsuario("a@exemplo.com"), noBanco.getUsuario().getId());
    }

    @Test
    void usuarioAEditaEApagaAPropriaTransacao() throws Exception {
        var tokenA = novoUsuarioComToken("A", "a@exemplo.com");
        long idDeA = criarTransacao(tokenA, new BigDecimal("10"));

        var edicao = new HashMap<>(transacaoValida(new BigDecimal("12.5")));
        edicao.put("observation", "editada pelo A");
        putTransacao(tokenA, idDeA, edicao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idDeA))
                .andExpect(jsonPath("$.observation").value("editada pelo A"));

        var noBanco = transactionRepository.findById(idDeA).orElseThrow();
        assertEquals("editada pelo A", noBanco.getObservation());
        assertEquals(0, new BigDecimal("12.5").compareTo(noBanco.getQuantity()));

        mockMvc.perform(delete(ROTA + "/" + idDeA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertTrue(transactionRepository.findById(idDeA).isEmpty());
        mockMvc.perform(get(ROTA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions postTransacao(String token, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post(ROTA)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(corpo)));
    }

    private ResultActions putTransacao(String token, long id, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put(ROTA + "/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(corpo)));
    }

    private Long idDoUsuario(String email) {
        return ((Usuario) usuarioRepository.findByEmail(email)).getId();
    }
}
