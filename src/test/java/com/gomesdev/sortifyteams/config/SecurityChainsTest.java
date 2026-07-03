package com.gomesdev.sortifyteams.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gomesdev.sortifyteams.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica o isolamento das duas cadeias de segurança (plan.md, D1 / T005). */
@AutoConfigureMockMvc
class SecurityChainsTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/api/** sem token responde 401, sem redirect de login")
    void apiSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/rachas"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/auth/** é público (não exige token)")
    void apiAuthEPublico() throws Exception {
        // 404 (rota ainda não implementada), nunca 401/redirect
        mockMvc.perform(get("/api/auth/ping"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/admin/** sem sessão redireciona para o form de login")
    void adminSemSessaoRedirecionaParaLogin() throws Exception {
        mockMvc.perform(get("/admin/solicitacoes"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @DisplayName("/admin/** exige papel ADMIN (usuário comum → 403)")
    void adminExigePapelAdmin() throws Exception {
        mockMvc.perform(get("/admin/solicitacoes").with(user("jogador").roles("JOGADOR")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rotas fora de /api e /admin são negadas (catch-all)")
    void foraDasCadeiasENegado() throws Exception {
        mockMvc.perform(get("/qualquer-coisa").with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
}
