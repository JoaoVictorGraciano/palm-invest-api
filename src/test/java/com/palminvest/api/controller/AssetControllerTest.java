package com.palminvest.api.controller;

import com.palminvest.api.IntegracaoTestBase;
import com.palminvest.api.ProvedoresFalsos.Resposta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.IntStream;

import static com.palminvest.api.RespostasDeMercado.ativo;
import static com.palminvest.api.RespostasDeMercado.buscaCoinGecko;
import static com.palminvest.api.RespostasDeMercado.listaBrapi;
import static com.palminvest.api.RespostasDeMercado.moeda;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /api/assets/search: mesmas regras de classificação e ordenação que o front aplicava, agora no servidor
class AssetControllerTest extends IntegracaoTestBase {

    private static final String LISTA_BRAPI = "/brapi/quote/list";
    private static final String BUSCA_COINGECKO = "/coingecko/search";

    private String token;

    @BeforeEach
    void logar() throws Exception {
        token = novoUsuarioComToken("A", "a@exemplo.com");
    }

    // --- autenticação e validação ---

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/assets/search").param("q", "petr"))
                .andExpect(status().isUnauthorized());
        assertTrue(PROVEDORES.requisicoes().isEmpty());
    }

    @Test
    void textoCurtoOuLongoDemaisRetorna400() throws Exception {
        buscar("p", "ALL").andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("q"));
        buscar("x".repeat(51), "ALL").andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].campo").value("q"));
        mockMvc.perform(get("/api/assets/search").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("q"));
        assertTrue(PROVEDORES.requisicoes().isEmpty());
    }

    @Test
    void classeInvalidaRetorna400ComValoresAceitos() throws Exception {
        buscar("petr", "ACAO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("class"))
                .andExpect(jsonPath("$[0].mensagem").value(containsString("ALL, STOCK, FII, ETF, BDR, CRYPTO")));
    }

    // --- provedores, classes e ordenação ---

    @Test
    void semClasseJuntaBrapiECoinGeckoComAClasseDeCadaUmEEnviaAsCredenciais() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 200, listaBrapi(ativo("PETR4", "PETROBRAS", "stock", "stock"), ativo("petr3", null, null, null)));
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko(moeda("petro-coin", "Petro", "petr", 900)));

        buscar(" petr ", "ALL")
                .andExpect(status().isOk())
                // O símbolo igual ao texto digitado vem primeiro
                .andExpect(jsonPath("$.suggestions[0].symbol").value("PETR"))
                .andExpect(jsonPath("$.suggestions[0].name").value("Petro"))
                .andExpect(jsonPath("$.suggestions[0].assetClass").value("CRYPTO"))
                .andExpect(jsonPath("$.suggestions[1].symbol").value("PETR4"))
                .andExpect(jsonPath("$.suggestions[1].assetClass").value("STOCK"))
                .andExpect(jsonPath("$.suggestions[2].symbol").value("PETR3"))
                .andExpect(jsonPath("$.suggestions[2].name").value("PETR3"))
                .andExpect(jsonPath("$.failedProviders", hasSize(0)));

        var brapi = PROVEDORES.requisicoes(LISTA_BRAPI).getFirst();
        assertEquals("PETR", brapi.query().get("search"));
        assertEquals("6", brapi.query().get("limit"));
        assertEquals("Bearer " + BRAPI_TOKEN_TESTE, brapi.headers().get("authorization"));
        var coingecko = PROVEDORES.requisicoes(BUSCA_COINGECKO).getFirst();
        assertEquals("PETR", coingecko.query().get("query"));
        assertEquals(COINGECKO_KEY_TESTE, coingecko.headers().get("x-cg-demo-api-key"));
    }

    @Test
    void classificaOsAtivosDaBrapiPeloTypeESubType() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 200, listaBrapi(
                ativo("PETR4", "PETROBRAS", "stock", "stock"),
                ativo("TAEE11", "TAESA", "stock", "unit"),
                ativo("MXRF11", "MAXI RENDA", "fund", "fii"),
                ativo("BOVA11", "ISHARES IBOVESPA", "fund", "etf"),
                ativo("AAPL34", "APPLE", "bdr", "bdr"),
                ativo("SNAG11", "SUNO AGRO", "fund", "fi-agro")));
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko());

        buscar("11", "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[?(@.symbol=='PETR4')].assetClass").value("STOCK"))
                .andExpect(jsonPath("$.suggestions[?(@.symbol=='TAEE11')].assetClass").value("STOCK"))
                .andExpect(jsonPath("$.suggestions[?(@.symbol=='MXRF11')].assetClass").value("FII"))
                .andExpect(jsonPath("$.suggestions[?(@.symbol=='BOVA11')].assetClass").value("ETF"))
                .andExpect(jsonPath("$.suggestions[?(@.symbol=='AAPL34')].assetClass").value("BDR"))
                .andExpect(jsonPath("$.suggestions[?(@.symbol=='SNAG11')].assetClass").value("FII"));
    }

    @Test
    void ficaComUmaCriptoPorSimboloADeMaiorValorDeMercadoEDescartaSimbolosInvalidos() throws Exception {
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko(
                moeda("clone-sem-ranking", "Clone", "AAA", null),
                moeda("clone-pequeno", "Clone pequeno", "aaa", 800),
                moeda("original", "Original", "AAA", 5),
                moeda("com-espaco", "Inválida", "A B", 1)));

        buscar("aaa", "CRYPTO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].symbol").value("AAA"))
                .andExpect(jsonPath("$.suggestions[0].name").value("Original"));
    }

    @Test
    void ordenaPorSimboloIgualDepoisInicioDoSimboloOuNomeDepoisORestoNaOrdemDoProvedor() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 200, listaBrapi(
                ativo("OBTC3", "ORANJEBTC S.A.", "stock", "stock"),
                ativo("BTCI11", "BTG PACTUAL CREDITO IMOBILIARIO", "fund", "fii")));
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko(
                moeda("bitget-wrapped-btc", "Bitget Wrapped BTC", "BGBTC", 267),
                moeda("bitcoin-pro", "Bitcoin Pro", "BTCP", 1582),
                moeda("bitcoin", "Bitcoin", "BTC", 1)));

        buscar("btc", "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[*].symbol").value(org.hamcrest.Matchers.contains("BTC", "BTCI11", "BTCP", "OBTC3", "BGBTC")));
    }

    @Test
    void comBIOBitcoinVemAntesDeMoedasPequenasComSimboloComecandoPorBI() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 200, listaBrapi(
                ativo("MOTV3", "MOTIVA INFRAESTRUTURA DE MOBILIDADE S.A.", "stock", "stock"),
                ativo("KLBN4", "KLABIN S.A.", "stock", "stock")));
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko(
                moeda("arbitrum", "Arbitrum", "ARB", 71),
                moeda("bio-protocol", "Bio Protocol", "BIO", 300),
                moeda("bitcoin-cash", "Bitcoin Cash", "BCH", 22),
                moeda("bitcoin", "Bitcoin", "BTC", 1)));

        buscar("BI", "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[*].symbol").value(org.hamcrest.Matchers.contains("BTC", "BCH", "BIO", "MOTV3", "KLBN4", "ARB")));
    }

    @Test
    void simboloExatoDeMoedaPequenaNaoEhCortadoPeloLimite() throws Exception {
        var moedas = IntStream.range(0, 8).mapToObj(i -> moeda("grande-" + i, "Grande " + i, "EEEG" + i, i + 1)).toArray(String[]::new);
        var todas = java.util.stream.Stream.concat(java.util.Arrays.stream(moedas), java.util.stream.Stream.of(moeda("eee-pequena", "Pequena", "EEE", 4000)))
                .toArray(String[]::new);
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko(todas));

        buscar("eee", "CRYPTO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(6)))
                .andExpect(jsonPath("$.suggestions[0].symbol").value("EEE"))
                .andExpect(jsonPath("$.suggestions[0].name").value("Pequena"));
    }

    @Test
    void encontraENAEthenaNaBuscaDeCripto() throws Exception {
        // Resposta no formato real da CoinGecko para "ENA": moedas maiores com "Ethena"/"ena" no nome,
        // um clone de ENA sem ranking e a Ethena (ENA) no meio da lista
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko(
                moeda("ethena-usde", "Ethena USDe", "USDE", 31),
                moeda("ethena", "Ethena", "ENA", 58),
                moeda("ethena-staked-usde", "Ethena Staked USDe", "SUSDE", 74),
                moeda("arena-token", "Arena", "ARENA", 1450),
                moeda("ena-clone", "ENA Clone", "ENA", null),
                moeda("serenity-shield", "Serenity Shield", "SERSH", 1210)));

        buscar("ENA", "CRYPTO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].symbol").value("ENA"))
                .andExpect(jsonPath("$.suggestions[0].name").value("Ethena"))
                .andExpect(jsonPath("$.suggestions[0].assetClass").value("CRYPTO"))
                .andExpect(jsonPath("$.suggestions[?(@.symbol=='ENA')]", hasSize(1)))
                .andExpect(jsonPath("$.failedProviders", hasSize(0)));

        // CRYPTO consulta só a CoinGecko, com o texto como veio
        assertEquals(1, PROVEDORES.requisicoes().size());
        assertEquals("ENA", PROVEDORES.requisicoes(BUSCA_COINGECKO).getFirst().query().get("query"));
    }

    @Test
    void enaContinuaNoTopoNaBuscaSemClasseMesmoComAcoesDaBrapi() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 200, listaBrapi(ativo("ENAT3", "ENAUTA PARTICIPAÇÕES S.A.", "stock", "stock")));
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko(
                moeda("ethena-usde", "Ethena USDe", "USDE", 31),
                moeda("ethena", "Ethena", "ENA", 58)));

        buscar("ena", "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[*].symbol").value(org.hamcrest.Matchers.contains("ENA", "ENAT3", "USDE")));
    }

    @Test
    void comClasseDaB3ConsultaSoABrapiPeloTipoCertoEDevolveSoAClassePedida() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 200, listaBrapi(
                ativo("MXRF11", "MAXI RENDA", "fund", "fii"),
                ativo("BOVA11", "ISHARES IBOVESPA", "fund", "etf")));

        buscar("11", "ETF")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].symbol").value("BOVA11"))
                .andExpect(jsonPath("$.suggestions[0].assetClass").value("ETF"));

        assertEquals(1, PROVEDORES.requisicoes().size());
        var brapi = PROVEDORES.requisicoes(LISTA_BRAPI).getFirst();
        assertEquals("fund", brapi.query().get("type"));
        assertEquals("30", brapi.query().get("limit"));
    }

    @Test
    void cadaClasseDaB3UsaOTipoCorrespondenteDaBrapi() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 200, listaBrapi());

        buscar("xx", "STOCK").andExpect(status().isOk());
        buscar("xx", "BDR").andExpect(status().isOk());
        buscar("xx", "FII").andExpect(status().isOk());

        assertEquals(java.util.List.of("stock", "bdr", "fund"),
                PROVEDORES.requisicoes(LISTA_BRAPI).stream().map(r -> r.query().get("type")).toList());
    }

    // --- falhas e cache ---

    @Test
    void mantemAsSugestoesDeUmProvedorQuandoOOutroFalha() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 503, "{\"message\":\"fora do ar\"}");
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko(moeda("bbb-coin", "Bbb", "BBB", 3)));

        buscar("bbb", "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].symbol").value("BBB"))
                .andExpect(jsonPath("$.failedProviders[0]").value("brapi"));
    }

    @Test
    void todosOsProvedoresConsultadosFalhandoRetorna503() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 500, "{}");
        PROVEDORES.quando(BUSCA_COINGECKO, r -> Resposta.comAtraso(1500, 200, buscaCoinGecko()));

        buscar("bbb", "ALL")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value(containsString("Brapi respondeu com erro (HTTP 500)")))
                .andExpect(jsonPath("$.mensagem").value(containsString("CoinGecko não respondeu a tempo")));
    }

    @Test
    void buscaRepetidaUsaOCacheEFalhaNaoFicaNoCache() throws Exception {
        PROVEDORES.quando(LISTA_BRAPI, 500, "{}");
        PROVEDORES.quando(BUSCA_COINGECKO, 200, buscaCoinGecko(moeda("bitcoin", "Bitcoin", "BTC", 1)));

        buscar("btc", "ALL").andExpect(status().isOk()).andExpect(jsonPath("$.failedProviders[0]").value("brapi"));
        // A Brapi voltou: como a falha não foi guardada, a próxima busca tenta de novo; a CoinGecko vem do cache
        PROVEDORES.quando(LISTA_BRAPI, 200, listaBrapi(ativo("BTCI11", "BTG CRI", "fund", "fii")));
        buscar("BTC", "ALL").andExpect(status().isOk()).andExpect(jsonPath("$.suggestions", hasSize(2)));
        buscar("btc", "ALL").andExpect(status().isOk()).andExpect(jsonPath("$.suggestions", hasSize(2)));

        assertEquals(2, PROVEDORES.requisicoes(LISTA_BRAPI).size());
        assertEquals(1, PROVEDORES.requisicoes(BUSCA_COINGECKO).size());
    }

    private ResultActions buscar(String q, String classe) throws Exception {
        return mockMvc.perform(get("/api/assets/search").param("q", q).param("class", classe)
                .header("Authorization", "Bearer " + token));
    }
}
