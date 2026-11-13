package com.palminvest.api.controller;

import com.palminvest.api.IntegracaoTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutenticacaoControllerTest extends IntegracaoTestBase {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void cadastroValidoRetorna201SemSenhaESalvaHashBCrypt() throws Exception {
        postCadastro("Joao", "joao@exemplo.com", SENHA_TESTE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Joao"))
                .andExpect(jsonPath("$.email").value("joao@exemplo.com"))
                .andExpect(jsonPath("$.senha").doesNotExist());

        var usuario = usuarioRepository.findByEmail("joao@exemplo.com");
        assertNotNull(usuario);
        assertNotEquals(SENHA_TESTE, usuario.getPassword());
        assertTrue(usuario.getPassword().startsWith("$2"), "senha deveria estar em BCrypt");
        assertTrue(passwordEncoder.matches(SENHA_TESTE, usuario.getPassword()));
    }

    @Test
    void cadastroNormalizaEmailParaMinusculasSemEspacos() throws Exception {
        postCadastro("Joao", "  Joao@Exemplo.COM  ", SENHA_TESTE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("joao@exemplo.com"));

        assertNotNull(usuarioRepository.findByEmail("joao@exemplo.com"));
    }

    @Test
    void cadastroComEmailRepetidoRetorna409() throws Exception {
        cadastrarUsuario("Joao", "joao@exemplo.com");

        // Mesmo e-mail em maiúsculas e com espaços: a normalização faz cair no conflito
        postCadastro("Outro", "  JOAO@EXEMPLO.COM ", SENHA_TESTE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("E-mail já cadastrado"));

        assertEquals(1, usuarioRepository.count());
    }

    @Test
    void cadastroInvalidoRetorna400ComCamposComErro() throws Exception {
        postCadastro("X", "invalido", "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[*].campo", hasItems("email", "senha")));

        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void loginCorretoRetornaToken() throws Exception {
        cadastrarUsuario("Joao", "joao@exemplo.com");

        postLogin("joao@exemplo.com", SENHA_TESTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void loginComEmailEmMaiusculasEEspacosFunciona() throws Exception {
        cadastrarUsuario("Joao", "joao@exemplo.com");

        var token = jsonMapper.readTree(postLogin(" JOAO@Exemplo.com ", SENHA_TESTE)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("token").asString();

        assertFalse(token.isBlank());
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        cadastrarUsuario("Joao", "joao@exemplo.com");

        postLogin("joao@exemplo.com", "SenhaErrada999")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meDevolveNomeEEmailDoDonoDoToken() throws Exception {
        var token = novoUsuarioComToken("Joao", "joao@exemplo.com");
        novoUsuarioComToken("Outro", "outro@exemplo.com"); // não pode vazar para a resposta de Joao

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Joao"))
                .andExpect(jsonPath("$.email").value("joao@exemplo.com"))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void meSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginComEmailInexistenteRetorna401() throws Exception {
        postLogin("ninguem@exemplo.com", SENHA_TESTE)
                .andExpect(status().isUnauthorized());
    }

    private ResultActions postCadastro(String nome, String email, String senha) throws Exception {
        var corpo = Map.of("nome", nome, "email", email, "senha", senha);
        return mockMvc.perform(post("/auth/cadastrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(corpo)));
    }

    private ResultActions postLogin(String email, String senha) throws Exception {
        var corpo = Map.of("email", email, "senha", senha);
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(corpo)));
    }
}
