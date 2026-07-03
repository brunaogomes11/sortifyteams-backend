package com.gomesdev.sortifyteams.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomesdev.sortifyteams.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Fluxos de autenticação (FR-001/002/003, C13, D3). */
@AutoConfigureMockMvc
class AuthFlowTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String registroJson(String username, String role) {
        return """
                {
                  "nomeCompleto": "Fulano de Tal",
                  "username": "%s",
                  "email": "%s@teste.com",
                  "senha": "senha123",
                  "role": "%s"
                }
                """.formatted(username, username, role);
    }

    private String loginJson(String username, String senha) {
        return """
                {"username": "%s", "senha": "%s"}
                """.formatted(username, senha);
    }

    @Test
    @DisplayName("jogador se registra, recebe tokens e acessa rota autenticada")
    void jogadorRegistraELoga() throws Exception {
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson("jogador1", "JOGADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.status").value("APROVADO"))
                .andReturn();

        String accessToken = objectMapper.readTree(registro.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/usuarios/busca").param("q", "jog")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("dono de quadra nasce PENDENTE, sem tokens, e login responde 403 (FR-003)")
    void donoPendenteNaoRecebeToken() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson("dono1", "DONO_QUADRA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.usuario.status").value("PENDENTE"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("dono1", "senha123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("username e e-mail duplicados respondem 400")
    void duplicadosRespondem400() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson("dup1", "JOGADOR")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson("dup1", "JOGADOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("credenciais inválidas respondem 401")
    void credenciaisInvalidasRespondem401() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson("jogador401", "JOGADOR")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("jogador401", "senha-errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh rotaciona o token; reuso do antigo revoga a família (D3)")
    void refreshRotacionaEDetectaReuso() throws Exception {
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson("jogador2", "JOGADOR")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode tokens = objectMapper.readTree(registro.getResponse().getContentAsString());
        String refresh1 = tokens.get("refreshToken").asText();

        // 1ª rotação: ok, tokens novos
        MvcResult primeira = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refresh1 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String refresh2 = objectMapper.readTree(primeira.getResponse().getContentAsString())
                .get("refreshToken").asText();
        assertThat(refresh2).isNotEqualTo(refresh1);

        // Reuso do token antigo: 401 e família revogada
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refresh1 + "\"}"))
                .andExpect(status().isUnauthorized());

        // O token novo também foi revogado pela detecção de reuso
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refresh2 + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout revoga o refresh token")
    void logoutRevogaRefresh() throws Exception {
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson("jogador3", "JOGADOR")))
                .andExpect(status().isCreated())
                .andReturn();

        String refresh = objectMapper.readTree(registro.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
