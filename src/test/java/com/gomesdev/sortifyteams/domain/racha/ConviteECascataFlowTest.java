package com.gomesdev.sortifyteams.domain.racha;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.atomic.AtomicInteger;

/** Fase 6: convite (C9), sair do racha e cancelamentos em cascata (C10). */
@AutoConfigureMockMvc
class ConviteECascataFlowTest extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private int seq;
    private String tokenOrganizador;
    private String tokenConvidado;

    @BeforeEach
    void registraUsuarios() throws Exception {
        seq = SEQ.incrementAndGet();
        tokenOrganizador = registrar("org-conv" + seq, "JOGADOR", false);
        tokenConvidado = registrar("convidado" + seq, "JOGADOR", false);
    }

    private String registrar(String username, String role, boolean aprovarDono) throws Exception {
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Usuario Convite", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "%s"}
                                """.formatted(username, username, role)))
                .andExpect(status().isCreated())
                .andReturn();
        if (aprovarDono) {
            var dono = usuarioRepository.findByUsername(username).orElseThrow();
            dono.setStatus(StatusUsuarioEnum.APROVADO);
            usuarioRepository.save(dono);
            MvcResult login = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"%s\", \"senha\": \"senha123\"}".formatted(username)))
                    .andExpect(status().isOk())
                    .andReturn();
            return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        }
        return objectMapper.readTree(registro.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode criarRacha(String token, Integer limiteVagas) throws Exception {
        MvcResult esportes = mockMvc.perform(get("/api/esportes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String esporteId = objectMapper.readTree(esportes.getResponse().getContentAsString())
                .get(0).get("id").asText();
        String limite = limiteVagas != null ? ", \"limiteVagas\": " + limiteVagas : "";
        MvcResult racha = mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"esporteId\": \"%s\", \"qtdTimes\": 2%s}".formatted(esporteId, limite)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(racha.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("convidado vê a prévia, entra pelo token e depois sai do racha (C9)")
    void entrarESairPeloConvite() throws Exception {
        JsonNode racha = criarRacha(tokenOrganizador, null);
        String token = racha.get("tokenConvite").asText();
        String rachaId = racha.get("id").asText();

        mockMvc.perform(get("/api/convites/" + token)
                        .header("Authorization", "Bearer " + tokenConvidado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jaParticipa").value(false))
                .andExpect(jsonPath("$.organizador").value("Usuario"));

        mockMvc.perform(post("/api/convites/" + token + "/entrar")
                        .header("Authorization", "Bearer " + tokenConvidado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivelTecnico\": 4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participantes.length()").value(1));

        // Entrar de novo → 400
        mockMvc.perform(post("/api/convites/" + token + "/entrar")
                        .header("Authorization", "Bearer " + tokenConvidado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivelTecnico\": 4}"))
                .andExpect(status().isBadRequest());

        // Convidado agora vê o racha na lista dele e consegue sair
        mockMvc.perform(get("/api/rachas")
                        .header("Authorization", "Bearer " + tokenConvidado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(rachaId));

        mockMvc.perform(delete("/api/rachas/" + rachaId + "/participantes/me")
                        .header("Authorization", "Bearer " + tokenConvidado))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rachas/" + rachaId)
                        .header("Authorization", "Bearer " + tokenOrganizador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes.length()").value(0));
    }

    @Test
    @DisplayName("limite de vagas bloqueia a entrada pelo convite (C9)")
    void limiteDeVagasNoConvite() throws Exception {
        JsonNode racha = criarRacha(tokenOrganizador, 1);
        String token = racha.get("tokenConvite").asText();

        // Organizador preenche a única vaga com um avulso
        mockMvc.perform(post("/api/rachas/" + racha.get("id").asText() + "/participantes")
                        .header("Authorization", "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeAvulso\": \"Fulano\", \"nivelTecnico\": 3}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/convites/" + token + "/entrar")
                        .header("Authorization", "Bearer " + tokenConvidado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivelTecnico\": 4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]")
                        .value(org.hamcrest.Matchers.containsString("limite de vagas")));
    }

    @Test
    @DisplayName("cancelar o racha cancela a reserva, libera o slot e notifica o dono (C10)")
    void cancelarRachaCascateiaReserva() throws Exception {
        // Dono com quadra e grade
        String tokenDono = registrar("dono-conv" + seq, "DONO_QUADRA", true);
        MvcResult quadra = mockMvc.perform(post("/api/dono/quadras")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Arena Cascata\", \"endereco\": \"Rua C, 7\", \"contato\": \"(34) 97777-0000\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String quadraId = objectMapper.readTree(quadra.getResponse().getContentAsString()).get("id").asText();

        MvcResult grade = mockMvc.perform(put("/api/dono/quadras/" + quadraId + "/horarios")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"horarios": [{"diaSemana": 1, "horaInicio": "18:00", "horaFim": "19:00", "preco": 100.00}]}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String slotId = objectMapper.readTree(grade.getResponse().getContentAsString())
                .get("horarios").get(0).get("id").asText();

        // Racha com reserva
        JsonNode racha = criarRacha(tokenOrganizador, null);
        String rachaId = racha.get("id").asText();
        LocalDate segunda = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quadraId": "%s", "rachaId": "%s", "data": "%s", "quadraHorarioIds": ["%s"]}
                                """.formatted(quadraId, rachaId, segunda, slotId)))
                .andExpect(status().isCreated());

        // Cancela o racha → cascata
        mockMvc.perform(delete("/api/rachas/" + rachaId)
                        .header("Authorization", "Bearer " + tokenOrganizador))
                .andExpect(status().isNoContent());

        // Dono notificado e slot liberado
        mockMvc.perform(get("/api/notificacoes")
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("RESERVA_CANCELADA"));

        mockMvc.perform(get("/api/quadras/" + quadraId + "/disponibilidade")
                        .header("Authorization", "Bearer " + tokenOrganizador)
                        .param("data", segunda.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].disponivel").value(true));
    }

    @Test
    @DisplayName("dono cancela reserva: agenda mostra, organizador e participantes notificados (C10/T034)")
    void donoCancelaReservaNotificaJogadores() throws Exception {
        String tokenDono = registrar("dono2-conv" + seq, "DONO_QUADRA", true);
        MvcResult quadra = mockMvc.perform(post("/api/dono/quadras")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Arena Agenda\", \"endereco\": \"Rua D, 9\", \"contato\": \"(34) 96666-0000\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String quadraId = objectMapper.readTree(quadra.getResponse().getContentAsString()).get("id").asText();
        MvcResult grade = mockMvc.perform(put("/api/dono/quadras/" + quadraId + "/horarios")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"horarios": [{"diaSemana": 1, "horaInicio": "20:00", "horaFim": "21:00", "preco": 110.00}]}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String slotId = objectMapper.readTree(grade.getResponse().getContentAsString())
                .get("horarios").get(0).get("id").asText();

        // Racha com convidado cadastrado + reserva
        JsonNode racha = criarRacha(tokenOrganizador, null);
        String rachaId = racha.get("id").asText();
        mockMvc.perform(post("/api/convites/" + racha.get("tokenConvite").asText() + "/entrar")
                        .header("Authorization", "Bearer " + tokenConvidado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivelTecnico\": 3}"))
                .andExpect(status().isCreated());

        LocalDate segunda = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        MvcResult reservaCriada = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quadraId": "%s", "rachaId": "%s", "data": "%s", "quadraHorarioIds": ["%s"]}
                                """.formatted(quadraId, rachaId, segunda, slotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn();
        String reservaId = objectMapper.readTree(reservaCriada.getResponse().getContentAsString())
                .get("id").asText();

        // Dono aceita a solicitação (PENDENTE → CONFIRMADA)
        mockMvc.perform(post("/api/dono/reservas/" + reservaId + "/aceitar")
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));

        // Agenda do dono mostra a reserva confirmada com quem reservou
        mockMvc.perform(get("/api/dono/agenda")
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizadorNome").value("Usuario Convite"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMADA"));

        // Dono cancela
        mockMvc.perform(delete("/api/dono/reservas/" + reservaId)
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isNoContent());

        // Organizador e convidado notificados
        mockMvc.perform(get("/api/notificacoes")
                        .header("Authorization", "Bearer " + tokenOrganizador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("RESERVA_CANCELADA_DONO"));
        mockMvc.perform(get("/api/notificacoes")
                        .header("Authorization", "Bearer " + tokenConvidado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("RESERVA_CANCELADA_DONO"));
    }
}
