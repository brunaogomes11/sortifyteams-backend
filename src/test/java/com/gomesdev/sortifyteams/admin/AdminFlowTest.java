package com.gomesdev.sortifyteams.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomesdev.sortifyteams.IntegrationTestBase;
import com.gomesdev.sortifyteams.domain.notificacao.NotificacaoRepository;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository;
import com.gomesdev.sortifyteams.enums.StatusUsuarioEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Fluxo 8 — painel admin: login por sessão, aprovação/rejeição de donos (T022/T023). */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.admin.username=admin-teste",
        "app.admin.password=senha-admin-123",
})
class AdminFlowTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String registrarDono(String username) throws Exception {
        var resultado = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Dono Teste", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "DONO_QUADRA"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("usuario").get("id").asText();
    }

    @Test
    @DisplayName("admin seedado loga no form de login com sessão")
    void adminLogaNoPainel() throws Exception {
        mockMvc.perform(formLogin("/admin/login").user("admin-teste").password("senha-admin-123"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/admin/solicitacoes"));
    }

    @Test
    @DisplayName("aprovação libera o login do dono e gera notificação (FR-003/T024)")
    void aprovacaoLiberaLoginDoDono() throws Exception {
        String donoId = registrarDono("dono-aprovar");

        // Painel lista a solicitação
        mockMvc.perform(get("/admin/solicitacoes").with(user("admin-teste").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dono-aprovar")));

        // Aprova (POST com CSRF, como o form Thymeleaf faz)
        mockMvc.perform(post("/admin/solicitacoes/" + donoId + "/aprovar")
                        .with(user("admin-teste").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(usuarioRepository.findById(donoId).orElseThrow().getStatus())
                .isEqualTo(StatusUsuarioEnum.APROVADO);
        assertThat(notificacaoRepository.findTop50ByUsuarioIdOrderByCriadaEmDesc(donoId))
                .anyMatch(n -> n.getTipo().equals("DONO_APROVADO"));

        // Agora o login do dono emite tokens
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"dono-aprovar\", \"senha\": \"senha123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("rejeição notifica e mantém login bloqueado (C13)")
    void rejeicaoMantemBloqueado() throws Exception {
        String donoId = registrarDono("dono-rejeitar");

        mockMvc.perform(post("/admin/solicitacoes/" + donoId + "/rejeitar")
                        .with(user("admin-teste").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(usuarioRepository.findById(donoId).orElseThrow().getStatus())
                .isEqualTo(StatusUsuarioEnum.REJEITADO);
        assertThat(notificacaoRepository.findTop50ByUsuarioIdOrderByCriadaEmDesc(donoId))
                .anyMatch(n -> n.getTipo().equals("DONO_REJEITADO"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"dono-rejeitar\", \"senha\": \"senha123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST sem CSRF é recusado na cadeia admin")
    void csrfObrigatorioNoAdmin() throws Exception {
        String donoId = registrarDono("dono-csrf");
        mockMvc.perform(post("/admin/solicitacoes/" + donoId + "/aprovar")
                        .with(user("admin-teste").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
