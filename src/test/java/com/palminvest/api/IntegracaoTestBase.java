package com.palminvest.api;

import com.palminvest.api.repository.TransactionRepository;
import com.palminvest.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Base dos testes de integração: sobe a aplicação inteira com MockMvc, H2 em memória e chave JWT aleatória
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracaoTestBase {

    // H2 em memória; DB_CLOSE_DELAY=-1 mantém o banco vivo enquanto o contexto do Spring existir
    private static final String URL_BANCO_TESTE = "jdbc:h2:mem:palm;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    // Chave JWT nova a cada execução: nenhum segredo fica no repositório
    protected static final String JWT_SECRET_TESTE = gerarChaveAleatoria();

    protected static final String SENHA_TESTE = "SenhaDeTeste123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected TransactionRepository transactionRepository;

    @Autowired
    private DataSource dataSource;

    // @DynamicPropertySource tem a maior precedência: nem variável de ambiente (SPRING_DATASOURCE_URL)
    // consegue apontar os testes para o Postgres real, onde o create-drop apagaria as tabelas
    @DynamicPropertySource
    static void propriedadesDeTeste(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL_BANCO_TESTE);
        registry.add("api.security.token.secret", () -> JWT_SECRET_TESTE);
    }

    @BeforeEach
    void limparBanco() throws SQLException {
        // Trava de segurança: confere o banco realmente conectado antes de apagar qualquer coisa
        String urlConectada;
        try (var conexao = dataSource.getConnection()) {
            urlConectada = conexao.getMetaData().getURL();
        }
        assertTrue(urlConectada != null && urlConectada.startsWith("jdbc:h2:mem:"),
                "Testes recusaram limpar o banco: a conexão não é H2 em memória (" + urlConectada + ")");

        transactionRepository.deleteAll(); // transações primeiro, por causa da FK para usuarios
        usuarioRepository.deleteAll();
    }

    // Cadastra um usuário com SENHA_TESTE e confere o 201
    protected void cadastrarUsuario(String nome, String email) throws Exception {
        var corpo = Map.of("nome", nome, "email", email, "senha", SENHA_TESTE);
        mockMvc.perform(post("/auth/cadastrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated());
    }

    // Faz login com SENHA_TESTE e devolve o token JWT
    protected String loginToken(String email) throws Exception {
        var corpo = Map.of("email", email, "senha", SENHA_TESTE);
        var resposta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(resposta).get("token").asString();
    }

    // Atalho: cadastra, faz login e devolve o token
    protected String novoUsuarioComToken(String nome, String email) throws Exception {
        cadastrarUsuario(nome, email);
        return loginToken(email);
    }

    // Corpo válido de transação, para os testes alterarem só o que interessa
    protected Map<String, Object> transacaoValida(BigDecimal quantity) {
        return Map.of(
                "type", "BUY",
                "assetSymbol", "PETR4",
                "quantity", quantity,
                "price", new BigDecimal("38.20"),
                "transactionDate", LocalDateTime.now().minusDays(1).withNano(0).toString(),
                "broker", "Corretora Teste",
                "fee", new BigDecimal("1.50"),
                "observation", "criada no teste");
    }

    // Cria uma transação com o token informado, confere o 201 e devolve o id
    protected long criarTransacao(String token, BigDecimal quantity) throws Exception {
        var resposta = mockMvc.perform(post("/api/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(transacaoValida(quantity))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(resposta).get("id").asLong();
    }

    private static String gerarChaveAleatoria() {
        byte[] bytes = new byte[32]; // 256 bits, mínimo do HS256
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
