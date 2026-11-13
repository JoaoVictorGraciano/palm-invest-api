package com.palminvest.api.infra;

import com.palminvest.api.IntegracaoTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TratadorDeErrosTest.ControllerQueFalha.class)
class TratadorDeErrosTest extends IntegracaoTestBase {

    private static final String SEGREDO_INTERNO = "NullPointer na linha 42 do servico X";

    // Rota só dos testes, para provocar uma falha não tratada
    @RestController
    static class ControllerQueFalha {
        @GetMapping("/teste/erro-interno")
        public String falhar() {
            throw new IllegalStateException(SEGREDO_INTERNO);
        }
    }

    @Test
    void erroInesperadoRetorna500SemVazarDetalhes() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");

        mockMvc.perform(get("/teste/erro-interno").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").value("Erro interno no servidor"))
                .andExpect(content().string(not(containsString(SEGREDO_INTERNO))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void idComTipoInvalidoNoPathRetorna400() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");

        mockMvc.perform(delete("/api/transactions/abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].campo").value("id"));
    }

    @Test
    void rotaInexistenteAutenticadaRetorna404() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");

        mockMvc.perform(get("/api/nao-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Rota não encontrada"));
    }

    @Test
    void metodoNaoSuportadoRetorna405() throws Exception {
        var token = novoUsuarioComToken("A", "a@exemplo.com");

        mockMvc.perform(patch("/api/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.mensagem").value("Método não suportado"));
    }
}
