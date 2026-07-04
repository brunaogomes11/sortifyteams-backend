package com.gomesdev.sortifyteams.domain.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fluxo 4 — reserva de quadra (T030, regra crítica FR-008/FR-009/C8):
 * preço total, conflito com alternativas e corrida entre organizadores.
 */
@AutoConfigureMockMvc
class ReservaFlowTest extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenDono;
    private String tokenOrganizador;
    private String quadraId;
    private List<String> slotsSegunda; // 18-19 (120.00) e 19-20 (140.00)
    private LocalDate proximaSegunda;

    @BeforeEach
    void preparaQuadraComGrade() throws Exception {
        int seq = SEQ.incrementAndGet();
        proximaSegunda = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // Dono aprovado com quadra e grade de segunda-feira
        tokenDono = registrar("dono-res" + seq, "DONO_QUADRA", true);
        MvcResult quadra = mockMvc.perform(post("/api/dono/quadras")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Arena Reserva %d", "endereco": "Rua B, 45", "contato": "(34) 98888-0000"}
                                """.formatted(seq)))
                .andExpect(status().isCreated())
                .andReturn();
        quadraId = objectMapper.readTree(quadra.getResponse().getContentAsString()).get("id").asText();

        MvcResult grade = mockMvc.perform(put("/api/dono/quadras/" + quadraId + "/horarios")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"horarios": [
                                  {"diaSemana": 1, "horaInicio": "18:00", "horaFim": "19:00", "preco": 120.00},
                                  {"diaSemana": 1, "horaInicio": "19:00", "horaFim": "20:00", "preco": 140.00},
                                  {"diaSemana": 3, "horaInicio": "18:00", "horaFim": "19:00", "preco": 90.00}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode horarios = objectMapper.readTree(grade.getResponse().getContentAsString()).get("horarios");
        slotsSegunda = new java.util.ArrayList<>();
        for (JsonNode h : horarios) {
            if (h.get("diaSemana").asInt() == 1) {
                slotsSegunda.add(h.get("id").asText());
            }
        }

        // Organizador com racha aberto
        tokenOrganizador = registrar("org-res" + seq, "JOGADOR", false);
    }

    private String registrar(String username, String role, boolean aprovarDono) throws Exception {
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Usuario Reserva", "username": "%s",
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

    private String criarRacha(String token) throws Exception {
        MvcResult esportes = mockMvc.perform(get("/api/esportes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String esporteId = objectMapper.readTree(esportes.getResponse().getContentAsString())
                .get(0).get("id").asText();
        MvcResult racha = mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"esporteId\": \"%s\", \"qtdTimes\": 2}".formatted(esporteId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(racha.getResponse().getContentAsString()).get("id").asText();
    }

    private String reservaJson(String rachaId, List<String> slots) {
        String ids = slots.stream().map(s -> "\"" + s + "\"").collect(java.util.stream.Collectors.joining(","));
        return """
                {"quadraId": "%s", "rachaId": "%s", "data": "%s", "quadraHorarioIds": [%s]}
                """.formatted(quadraId, rachaId, proximaSegunda, ids);
    }

    @Test
    @DisplayName("reserva de 2 horários soma o preço total e vincula o racha (FR-008)")
    void reservaComPrecoTotal() throws Exception {
        String rachaId = criarRacha(tokenOrganizador);

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(rachaId, slotsSegunda)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precoTotal").value(260.00))
                .andExpect(jsonPath("$.horarios.length()").value(2))
                .andExpect(jsonPath("$.quadraContato").value("(34) 98888-0000"));

        // Racha vinculado à quadra e à data
        mockMvc.perform(get("/api/rachas/" + rachaId)
                        .header("Authorization", "Bearer " + tokenOrganizador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(proximaSegunda.toString()));

        // Disponibilidade marca os dois slots como ocupados
        mockMvc.perform(get("/api/quadras/" + quadraId + "/disponibilidade")
                        .header("Authorization", "Bearer " + tokenOrganizador)
                        .param("data", proximaSegunda.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].disponivel").value(false))
                .andExpect(jsonPath("$.slots[1].disponivel").value(false));
    }

    @Test
    @DisplayName("segundo organizador no mesmo slot recebe 409 com alternativas (C8)")
    void conflitoSequencialResponde409() throws Exception {
        String racha1 = criarRacha(tokenOrganizador);
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(racha1, List.of(slotsSegunda.get(0)))))
                .andExpect(status().isCreated());

        String token2 = registrar("org2-res" + SEQ.get(), "JOGADOR", false);
        String racha2 = criarRacha(token2);
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(racha2, List.of(slotsSegunda.get(0)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message[0]")
                        .value(org.hamcrest.Matchers.containsString("19:00–20:00")));
    }

    @Test
    @DisplayName("corrida: 2 requisições concorrentes → 1 sucesso e 1 conflito (C8/FR-009)")
    void corridaConcorrente() throws Exception {
        String racha1 = criarRacha(tokenOrganizador);
        String token2 = registrar("org3-res" + SEQ.get(), "JOGADOR", false);
        String racha2 = criarRacha(token2);

        record Tentativa(String token, String rachaId) {}
        List<Tentativa> tentativas = List.of(
                new Tentativa(tokenOrganizador, racha1),
                new Tentativa(token2, racha2));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> futuros = tentativas.stream()
                .map(t -> executor.submit(() -> {
                    largada.await();
                    return mockMvc.perform(post("/api/reservas")
                                    .header("Authorization", "Bearer " + t.token())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(reservaJson(t.rachaId(), List.of(slotsSegunda.get(0)))))
                            .andReturn().getResponse().getStatus();
                }))
                .toList();
        largada.countDown();

        List<Integer> statuses = new java.util.ArrayList<>();
        for (Future<Integer> futuro : futuros) {
            statuses.add(futuro.get());
        }
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    @DisplayName("horário de outro dia da semana responde 400")
    void diaErradoResponde400() throws Exception {
        String rachaId = criarRacha(tokenOrganizador);
        // slot de quarta (diaSemana 3) numa segunda-feira
        MvcResult quadraDet = mockMvc.perform(get("/api/quadras/" + quadraId)
                        .header("Authorization", "Bearer " + tokenOrganizador))
                .andExpect(status().isOk())
                .andReturn();
        String slotQuarta = null;
        for (JsonNode h : objectMapper.readTree(quadraDet.getResponse().getContentAsString()).get("horarios")) {
            if (h.get("diaSemana").asInt() == 3) slotQuarta = h.get("id").asText();
        }

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(rachaId, List.of(slotQuarta))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]")
                        .value(org.hamcrest.Matchers.containsString("outro dia da semana")));
    }

    @Test
    @DisplayName("cancelamento libera o slot para nova reserva e notifica o dono (C10)")
    void cancelamentoLiberaSlot() throws Exception {
        String racha1 = criarRacha(tokenOrganizador);
        MvcResult criada = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenOrganizador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(racha1, List.of(slotsSegunda.get(0)))))
                .andExpect(status().isCreated())
                .andReturn();
        String reservaId = objectMapper.readTree(criada.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/reservas/" + reservaId)
                        .header("Authorization", "Bearer " + tokenOrganizador))
                .andExpect(status().isNoContent());

        // Dono recebeu notificação de cancelamento
        mockMvc.perform(get("/api/notificacoes")
                        .header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("RESERVA_CANCELADA"));

        // Slot liberado: outro organizador reserva o mesmo horário
        String token2 = registrar("org4-res" + SEQ.get(), "JOGADOR", false);
        String racha2 = criarRacha(token2);
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(racha2, List.of(slotsSegunda.get(0)))))
                .andExpect(status().isCreated());
    }
}
