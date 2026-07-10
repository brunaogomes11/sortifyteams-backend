package com.gomesdev.sortifyteams.domain.racha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomesdev.sortifyteams.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Fluxo de integração do racha: criar → participantes → sorteio → concluir (Fase 2). */
@AutoConfigureMockMvc
class RachaFlowTest extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;

    @BeforeEach
    void registraOrganizador() throws Exception {
        String username = "organizador" + SEQ.incrementAndGet();
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Organizador Teste", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "JOGADOR"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        accessToken = objectMapper.readTree(registro.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String esporteId(String nome) throws Exception {
        MvcResult esportes = mockMvc.perform(get("/api/esportes")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode esporte : objectMapper.readTree(esportes.getResponse().getContentAsString())) {
            if (nome.equals(esporte.get("nome").asText())) {
                return esporte.get("id").asText();
            }
        }
        throw new IllegalStateException("Esporte não encontrado no seed: " + nome);
    }

    private String criarRachaFutsal(int qtdTimes, boolean balancear) throws Exception {
        MvcResult criado = mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "qtdTimes": %d, "balancearNivel": %b}
                                """.formatted(esporteId("Futsal"), qtdTimes, balancear)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.tokenConvite").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(criado.getResponse().getContentAsString()).get("id").asText();
    }

    private void adicionarAvulso(String rachaId, String nome, int nivel, boolean goleiro) throws Exception {
        mockMvc.perform(post("/api/rachas/" + rachaId + "/participantes")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeAvulso": "%s", "nivelTecnico": %d, "eGoleiro": %b}
                                """.formatted(nome, nivel, goleiro)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("racha com data no passado responde 400 (FIX 5)")
    void dataPassadaResponde400() throws Exception {
        mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "data": "2020-01-01"}
                                """.formatted(esporteId("Futsal"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]")
                        .value(org.hamcrest.Matchers.containsString("presente ou no futuro")));
    }

    @Test
    @DisplayName("data em formato brasileiro responde 400 com dica de formato, não 500 (FIX 4)")
    void dataFormatoBrasileiroResponde400() throws Exception {
        mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "data": "25/12/2026"}
                                """.formatted(esporteId("Futsal"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]")
                        .value(org.hamcrest.Matchers.containsString("AAAA-MM-DD")));
    }

    @Test
    @DisplayName("organizador que joga entra como participante na criação (FIX 10)")
    void organizadorJogaEntraComoParticipante() throws Exception {
        mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "organizadorJoga": true, "organizadorNivelTecnico": 4}
                                """.formatted(esporteId("Futsal"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participantes.length()").value(1))
                .andExpect(jsonPath("$.participantes[0].nivelTecnico").value(4));

        // Sem o nível técnico, a inclusão é recusada com orientação.
        mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "organizadorJoga": true}
                                """.formatted(esporteId("Futsal"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]")
                        .value(org.hamcrest.Matchers.containsString("nível técnico")));
    }

    @Test
    @DisplayName("racha sem nível técnico não balanceia por nível (usaNivelTecnico)")
    void semNivelTecnicoNaoBalanceia() throws Exception {
        mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "usaNivelTecnico": false, "balancearNivel": true}
                                """.formatted(esporteId("Futsal"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usaNivelTecnico").value(false))
                .andExpect(jsonPath("$.balancearNivel").value(false));
    }

    @Test
    @DisplayName("fluxo completo: criar racha futsal, 10 jogadores, sortear 2 times com goleiros, concluir")
    void fluxoCompleto() throws Exception {
        String rachaId = criarRachaFutsal(2, true);

        adicionarAvulso(rachaId, "Goleiro A", 3, true);
        adicionarAvulso(rachaId, "Goleiro B", 2, true);
        for (int i = 1; i <= 8; i++) {
            adicionarAvulso(rachaId, "Linha " + i, (i % 5) + 1, false);
        }

        // Sorteio: 2 times de 5, um goleiro em cada
        MvcResult sorteado = mockMvc.perform(post("/api/rachas/" + rachaId + "/sorteio")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode times = objectMapper.readTree(sorteado.getResponse().getContentAsString()).get("times");
        assertThat(times).hasSize(2);
        Set<String> vistos = new HashSet<>();
        for (JsonNode time : times) {
            assertThat(time.get("jogadores")).hasSize(5);
            long goleiros = 0;
            for (JsonNode jogador : time.get("jogadores")) {
                vistos.add(jogador.get("id").asText());
                if (jogador.get("eGoleiro").asBoolean()) goleiros++;
            }
            assertThat(goleiros).isEqualTo(1);
        }
        assertThat(vistos).hasSize(10);

        // Re-sorteio não pode colidir na UNIQUE (racha_id, numero) dos times.
        mockMvc.perform(post("/api/rachas/" + rachaId + "/sorteio")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.times.length()").value(2));

        // Concluir com duração do cronômetro (C1)
        mockMvc.perform(post("/api/rachas/" + rachaId + "/concluir")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracaoPartidaSeg\": 3600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.duracaoPartidaSeg").value(3600));

        // Lista Meus Rachas contém o racha concluído
        mockMvc.perform(get("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONCLUIDO"))
                .andExpect(jsonPath("$[0].qtdParticipantes").value(10));
    }

    @Test
    @DisplayName("editar times troca jogadores de time (PATCH /times)")
    void editarTimesTrocaJogadores() throws Exception {
        String rachaId = criarRachaFutsal(2, false);
        for (int i = 1; i <= 10; i++) {
            adicionarAvulso(rachaId, "Linha " + i, 3, false);
        }

        MvcResult sorteado = mockMvc.perform(post("/api/rachas/" + rachaId + "/sorteio")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode times = objectMapper.readTree(sorteado.getResponse().getContentAsString()).get("times");
        int numero1 = times.get(0).get("numero").asInt();
        int numero2 = times.get(1).get("numero").asInt();
        String jogadorDoTime1 = times.get(0).get("jogadores").get(0).get("id").asText();
        String jogadorDoTime2 = times.get(1).get("jogadores").get(0).get("id").asText();

        // Troca: jogador do time 1 vai para o time 2 e vice-versa.
        mockMvc.perform(patch("/api/rachas/" + rachaId + "/times")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"atribuicoes": [
                                  {"participanteId": "%s", "timeNumero": %d},
                                  {"participanteId": "%s", "timeNumero": %d}
                                ]}
                                """.formatted(jogadorDoTime1, numero2, jogadorDoTime2, numero1)))
                .andExpect(status().isOk());

        // O detalhe confirma que os dois trocaram de time.
        MvcResult detalhe = mockMvc.perform(get("/api/rachas/" + rachaId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode participantes = objectMapper.readTree(detalhe.getResponse().getContentAsString())
                .get("participantes");
        for (JsonNode participante : participantes) {
            String id = participante.get("id").asText();
            if (id.equals(jogadorDoTime1)) {
                assertThat(participante.get("timeNumero").asInt()).isEqualTo(numero2);
            }
            if (id.equals(jogadorDoTime2)) {
                assertThat(participante.get("timeNumero").asInt()).isEqualTo(numero1);
            }
        }

        // Time inexistente responde 400 com mensagem clara.
        mockMvc.perform(patch("/api/rachas/" + rachaId + "/times")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"atribuicoes": [{"participanteId": "%s", "timeNumero": 99}]}
                                """.formatted(jogadorDoTime1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(org.hamcrest.Matchers.containsString("não existe")));
    }

    @Test
    @DisplayName("goleiros fora do sorteio: grupo Goleiros à parte (número 0)")
    void goleirosForaDoSorteioGrupoAParte() throws Exception {
        String rachaId = criarRachaFutsal(2, false);

        // Desliga "incluir goleiros no sorteio" na edição do racha.
        mockMvc.perform(patch("/api/rachas/" + rachaId + "/config")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incluirGoleirosNoSorteio\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incluirGoleirosNoSorteio").value(false));

        adicionarAvulso(rachaId, "Goleiro A", 3, true);
        adicionarAvulso(rachaId, "Goleiro B", 2, true);
        for (int i = 1; i <= 8; i++) {
            adicionarAvulso(rachaId, "Linha " + i, 3, false);
        }

        MvcResult sorteado = mockMvc.perform(post("/api/rachas/" + rachaId + "/sorteio")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode times = objectMapper.readTree(sorteado.getResponse().getContentAsString()).get("times");
        JsonNode grupoGoleiros = null;
        int numerados = 0;
        for (JsonNode time : times) {
            if (time.get("numero").asInt() == 0) {
                grupoGoleiros = time;
            } else {
                numerados++;
                assertThat(time.get("jogadores")).hasSize(4);
                for (JsonNode jogador : time.get("jogadores")) {
                    assertThat(jogador.get("eGoleiro").asBoolean()).isFalse();
                }
            }
        }
        assertThat(numerados).isEqualTo(2);
        assertThat(grupoGoleiros).isNotNull();
        assertThat(grupoGoleiros.get("jogadores")).hasSize(2);
        for (JsonNode goleiro : grupoGoleiros.get("jogadores")) {
            assertThat(goleiro.get("eGoleiro").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("sorteio abaixo do mínimo do esporte responde 400 com mensagem clara (C6)")
    void sorteioAbaixoDoMinimo() throws Exception {
        String rachaId = criarRachaFutsal(2, false);
        // Futsal exige 4 por time → mínimo 8; adiciona só 6
        for (int i = 1; i <= 6; i++) {
            adicionarAvulso(rachaId, "Linha " + i, 3, false);
        }

        mockMvc.perform(post("/api/rachas/" + rachaId + "/sorteio")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(org.hamcrest.Matchers.containsString("pelo menos 8")));
    }

    @Test
    @DisplayName("limite de vagas impede participante extra (C9)")
    void limiteDeVagas() throws Exception {
        MvcResult criado = mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "qtdTimes": 2, "limiteVagas": 2}
                                """.formatted(esporteId("Futsal"))))
                .andExpect(status().isCreated())
                .andReturn();
        String rachaId = objectMapper.readTree(criado.getResponse().getContentAsString()).get("id").asText();

        adicionarAvulso(rachaId, "Jogador 1", 3, false);
        adicionarAvulso(rachaId, "Jogador 2", 3, false);

        mockMvc.perform(post("/api/rachas/" + rachaId + "/participantes")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeAvulso\": \"Extra\", \"nivelTecnico\": 3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quem não é organizador não sorteia nem cancela (403)")
    void apenasOrganizadorGerencia() throws Exception {
        String rachaId = criarRachaFutsal(2, false);

        // Segundo usuário
        MvcResult outro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Intruso", "username": "intruso%d",
                                 "email": "intruso%d@teste.com", "senha": "senha123", "role": "JOGADOR"}
                                """.formatted(SEQ.get(), SEQ.get())))
                .andExpect(status().isCreated())
                .andReturn();
        String outroToken = objectMapper.readTree(outro.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(post("/api/rachas/" + rachaId + "/sorteio")
                        .header("Authorization", "Bearer " + outroToken))
                .andExpect(status().isForbidden());
    }
}
