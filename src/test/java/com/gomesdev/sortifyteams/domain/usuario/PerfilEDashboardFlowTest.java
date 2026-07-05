package com.gomesdev.sortifyteams.domain.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomesdev.sortifyteams.IntegrationTestBase;
import com.gomesdev.sortifyteams.domain.notificacao.NotificacaoRepository;
import com.gomesdev.sortifyteams.domain.racha.LembreteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/** Fase 7: perfil (C11/FR-012), dashboard admin (C14) e lembrete de racha (T040). */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.local-path=target/test-uploads-perfil")
class PerfilEDashboardFlowTest extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LembreteService lembreteService;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String registrar(String username) throws Exception {
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Perfil Teste", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "JOGADOR"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(registro.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String criarRacha(String token, String esporteNome, String data) throws Exception {
        MvcResult esportes = mockMvc.perform(get("/api/esportes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String esporteId = null;
        for (JsonNode e : objectMapper.readTree(esportes.getResponse().getContentAsString())) {
            if (esporteNome.equals(e.get("nome").asText())) esporteId = e.get("id").asText();
        }
        String dataJson = data != null ? ", \"data\": \"" + data + "\"" : "";
        MvcResult racha = mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"esporteId\": \"%s\", \"qtdTimes\": 2%s}".formatted(esporteId, dataJson)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(racha.getResponse().getContentAsString()).get("id").asText();
    }

    private void concluir(String token, String rachaId) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/rachas/" + rachaId + "/concluir")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        mockMvc.perform(req).andExpect(status().isOk());
    }

    @Test
    @DisplayName("perfil calcula contador e esporte preferido do histórico, com override manual (C11)")
    void perfilComHistoricoEOverride() throws Exception {
        String token = registrar("perfil" + SEQ.incrementAndGet());

        mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rachasParticipados").value(0))
                .andExpect(jsonPath("$.esportePreferidoNome").doesNotExist());

        // 2 rachas de Futsal e 1 de Vôlei concluídos → preferido = Futsal
        concluir(token, criarRacha(token, "Futsal", null));
        concluir(token, criarRacha(token, "Futsal", null));
        concluir(token, criarRacha(token, "Vôlei", null));

        mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rachasParticipados").value(3))
                .andExpect(jsonPath("$.esportePreferidoNome").value("Futsal"))
                .andExpect(jsonPath("$.esportePreferidoManual").value(false));

        // Override manual para Vôlei
        MvcResult esportes = mockMvc.perform(get("/api/esportes")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        String voleiId = null;
        for (JsonNode e : objectMapper.readTree(esportes.getResponse().getContentAsString())) {
            if ("Vôlei".equals(e.get("nome").asText())) voleiId = e.get("id").asText();
        }
        mockMvc.perform(put("/api/perfil")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Perfil Editado", "contato": "(34) 95555-0000", "esportePreferidoId": "%s"}
                                """.formatted(voleiId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value("Perfil Editado"))
                .andExpect(jsonPath("$.esportePreferidoNome").value("Vôlei"))
                .andExpect(jsonPath("$.esportePreferidoManual").value(true));
    }

    @Test
    @DisplayName("upload de foto de perfil devolve URL pública")
    void fotoDePerfil() throws Exception {
        String token = registrar("perfilfoto" + SEQ.incrementAndGet());
        MockMultipartFile foto = new MockMultipartFile(
                "arquivo", "eu.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 0x01});

        mockMvc.perform(multipart("/api/perfil/foto")
                        .file(foto)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(org.hamcrest.Matchers.containsString("/files/perfis/")));
    }

    @Test
    @DisplayName("lembrete de racha do dia notifica organizador (T040)")
    void lembreteDeRachaDoDia() throws Exception {
        String username = "lembrete" + SEQ.incrementAndGet();
        String token = registrar(username);
        criarRacha(token, "Basquete", LocalDate.now().toString());

        int avisados = lembreteService.lembrarRachasDeHoje();
        assertThat(avisados).isGreaterThanOrEqualTo(1);

        String usuarioId = usuarioRepository.findByUsername(username).orElseThrow().getId();
        assertThat(notificacaoRepository.findTop50ByUsuarioIdOrderByCriadaEmDesc(usuarioId))
                .anyMatch(n -> n.getTipo().equals("LEMBRETE_RACHA"));
    }

    @Test
    @DisplayName("dashboard admin renderiza (C14)")
    void dashboardRenderiza() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(user("admin-dash").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dashboard")));
    }
}
