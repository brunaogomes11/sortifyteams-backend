package com.gomesdev.sortifyteams.domain.quadra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomesdev.sortifyteams.IntegrationTestBase;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository;
import com.gomesdev.sortifyteams.enums.StatusUsuarioEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Fluxo 7 — CRUD de quadras do dono, grade semanal e fotos (T025–T027). */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.local-path=target/test-uploads")
class QuadraFlowTest extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenDono;

    @BeforeEach
    void registraDonoAprovado() throws Exception {
        String username = "dono-quadra" + SEQ.incrementAndGet();
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Dono Quadra", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "DONO_QUADRA"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated());

        // Aprova direto no repositório (o fluxo de aprovação é coberto no AdminFlowTest)
        var dono = usuarioRepository.findByUsername(username).orElseThrow();
        dono.setStatus(StatusUsuarioEnum.APROVADO);
        usuarioRepository.save(dono);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"senha\": \"senha123\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        tokenDono = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String criarQuadra() throws Exception {
        MvcResult criada = mockMvc.perform(post("/api/dono/quadras")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Arena Central", "endereco": "Rua A, 123", "contato": "(34) 99999-0000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Arena Central"))
                .andReturn();
        return objectMapper.readTree(criada.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("dono cadastra quadra e define grade semanal válida")
    void cadastraQuadraComGrade() throws Exception {
        String quadraId = criarQuadra();

        mockMvc.perform(put("/api/dono/quadras/" + quadraId + "/horarios")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"horarios": [
                                  {"diaSemana": 1, "horaInicio": "18:00", "horaFim": "19:00", "preco": 120.00},
                                  {"diaSemana": 1, "horaInicio": "19:00", "horaFim": "20:00", "preco": 140.00},
                                  {"diaSemana": 6, "horaInicio": "09:00", "horaFim": "10:00", "preco": 100.00}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios.length()").value(3));

        mockMvc.perform(get("/api/dono/quadras")
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].horarios.length()").value(3));
    }

    @Test
    @DisplayName("grade com sobreposição no mesmo dia responde 400 (T027)")
    void gradeSobrepostaResponde400() throws Exception {
        String quadraId = criarQuadra();

        mockMvc.perform(put("/api/dono/quadras/" + quadraId + "/horarios")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"horarios": [
                                  {"diaSemana": 1, "horaInicio": "18:00", "horaFim": "20:00", "preco": 120.00},
                                  {"diaSemana": 1, "horaInicio": "19:00", "horaFim": "21:00", "preco": 140.00}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(org.hamcrest.Matchers.containsString("sobrepostos")));
    }

    @Test
    @DisplayName("faixa de 3 horas expande em 3 reservas de 1 hora com o preço por hora (FIX 15)")
    void faixaExpandeEmSlotsDeUmaHora() throws Exception {
        String quadraId = criarQuadra();

        mockMvc.perform(put("/api/dono/quadras/" + quadraId + "/horarios")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"horarios": [
                                  {"diaSemana": 5, "horaInicio": "18:00", "horaFim": "21:00", "preco": 100.00}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios.length()").value(3))
                .andExpect(jsonPath("$.horarios[0].horaInicio").value("18:00:00"))
                .andExpect(jsonPath("$.horarios[0].horaFim").value("19:00:00"))
                .andExpect(jsonPath("$.horarios[1].horaInicio").value("19:00:00"))
                .andExpect(jsonPath("$.horarios[2].horaInicio").value("20:00:00"))
                .andExpect(jsonPath("$.horarios[2].horaFim").value("21:00:00"))
                .andExpect(jsonPath("$.horarios[0].preco").value(100.00))
                .andExpect(jsonPath("$.horarios[2].preco").value(100.00));
    }

    @Test
    @DisplayName("faixa que não fecha hora inteira responde 400 (FIX 15)")
    void faixaQuebradaResponde400() throws Exception {
        String quadraId = criarQuadra();

        mockMvc.perform(put("/api/dono/quadras/" + quadraId + "/horarios")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"horarios": [
                                  {"diaSemana": 1, "horaInicio": "18:00", "horaFim": "19:30", "preco": 120.00}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(org.hamcrest.Matchers.containsString("horas inteiras")));
    }

    @Test
    @DisplayName("upload de foto grava no storage local e devolve URL (T026)")
    void uploadDeFoto() throws Exception {
        String quadraId = criarQuadra();

        MockMultipartFile foto = new MockMultipartFile(
                "arquivo", "quadra.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        MvcResult resultado = mockMvc.perform(multipart("/api/dono/quadras/" + quadraId + "/fotos")
                        .file(foto)
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fotos.length()").value(1))
                .andReturn();

        String key = objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("fotos").get(0).get("url").asText();
        assertThat(key).contains("/files/quadras/" + quadraId + "/");
        // Arquivo realmente gravado no disco
        try (var arquivos = Files.list(Path.of("target/test-uploads/quadras/" + quadraId))) {
            assertThat(arquivos.count()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("upload que não é imagem responde 400")
    void uploadNaoImagemResponde400() throws Exception {
        String quadraId = criarQuadra();
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "nota.txt", "text/plain", "oi".getBytes());

        mockMvc.perform(multipart("/api/dono/quadras/" + quadraId + "/fotos")
                        .file(arquivo)
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("jogador não acessa /api/dono/** (403)")
    void jogadorNaoAcessaAreaDoDono() throws Exception {
        String username = "jogador-intruso" + SEQ.get();
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Jogador", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "JOGADOR"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenJogador = objectMapper.readTree(registro.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/dono/quadras")
                        .header("Authorization", "Bearer " + tokenJogador))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("dono não acessa quadra de outro dono (403)")
    void quadraDeOutroDonoInacessivel() throws Exception {
        String quadraId = criarQuadra();

        // Outro dono aprovado
        String outro = "outro-dono" + SEQ.get();
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Outro Dono", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "DONO_QUADRA"}
                                """.formatted(outro, outro)))
                .andExpect(status().isCreated());
        var dono2 = usuarioRepository.findByUsername(outro).orElseThrow();
        dono2.setStatus(StatusUsuarioEnum.APROVADO);
        usuarioRepository.save(dono2);
        MvcResult login2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"senha\": \"senha123\"}".formatted(outro)))
                .andExpect(status().isOk())
                .andReturn();
        String token2 = objectMapper.readTree(login2.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/dono/quadras/" + quadraId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());
    }
}
