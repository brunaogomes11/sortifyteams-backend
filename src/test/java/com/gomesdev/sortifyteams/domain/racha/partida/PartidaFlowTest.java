package com.gomesdev.sortifyteams.domain.racha.partida;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomesdev.sortifyteams.IntegrationTestBase;
import com.gomesdev.sortifyteams.domain.racha.Racha;
import com.gomesdev.sortifyteams.domain.racha.RachaExpiracaoService;
import com.gomesdev.sortifyteams.domain.racha.RachaRepository;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fluxo ao vivo do racha: iniciar → partidas (vencedor fica) → gols por
 * qualquer membro → critérios de empate → concluir com histórico.
 */
@AutoConfigureMockMvc
class PartidaFlowTest extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RachaRepository rachaRepository;

    @Autowired
    private RachaExpiracaoService rachaExpiracaoService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String organizadorToken;
    private String membroToken;
    private String intrusoToken;

    @BeforeEach
    void registraUsuarios() throws Exception {
        int n = SEQ.incrementAndGet();
        organizadorToken = registrar("orgpartida" + n);
        membroToken = registrar("membropartida" + n);
        intrusoToken = registrar("intrusopartida" + n);
    }

    // ---------- helpers ----------

    private String registrar(String username) throws Exception {
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Usuario Teste", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "JOGADOR"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(registro).get("accessToken").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String esporteId(String nome) throws Exception {
        MvcResult esportes = mockMvc.perform(get("/api/esportes")
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode esporte : json(esportes)) {
            if (nome.equals(esporte.get("nome").asText())) {
                return esporte.get("id").asText();
            }
        }
        throw new IllegalStateException("Esporte não encontrado no seed: " + nome);
    }

    /** Cria racha futsal; {@code criteriosExtras} são campos JSON adicionais (ex.: critérios de empate). */
    private JsonNode criarRachaFutsal(int qtdTimes, String criteriosExtras) throws Exception {
        MvcResult criado = mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "qtdTimes": %d%s}
                                """.formatted(esporteId("Futsal"), qtdTimes,
                                criteriosExtras != null ? ", " + criteriosExtras : "")))
                .andExpect(status().isCreated())
                .andReturn();
        return json(criado);
    }

    private void adicionarAvulso(String rachaId, String nome) throws Exception {
        mockMvc.perform(post("/api/rachas/" + rachaId + "/participantes")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeAvulso\": \"%s\", \"nivelTecnico\": 3}".formatted(nome)))
                .andExpect(status().isCreated());
    }

    /**
     * Racha futsal pronto e ao vivo: qtdTimes*4 jogadores (mínimo do futsal),
     * sendo um deles o "membro" (usuário cadastrado que entrou pelo convite),
     * times sorteados e racha iniciado.
     */
    private String prepararRachaAoVivo(int qtdTimes, String criteriosExtras) throws Exception {
        JsonNode racha = criarRachaFutsal(qtdTimes, criteriosExtras);
        String rachaId = racha.get("id").asText();
        String tokenConvite = racha.get("tokenConvite").asText();

        for (int i = 1; i <= qtdTimes * 4 - 1; i++) {
            adicionarAvulso(rachaId, "Linha " + i);
        }
        mockMvc.perform(post("/api/convites/" + tokenConvite + "/entrar")
                        .header("Authorization", "Bearer " + membroToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivelTecnico\": 3}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rachas/" + rachaId + "/sorteio")
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rachas/" + rachaId + "/iniciar")
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk());
        return rachaId;
    }

    private String criarPartida(String rachaId, int timeA, int timeB, Integer duracaoSeg) throws Exception {
        String duracao = duracaoSeg != null ? ", \"duracaoPrevistaSeg\": " + duracaoSeg : "";
        MvcResult criada = mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumeroA\": %d, \"timeNumeroB\": %d%s}".formatted(timeA, timeB, duracao)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(criada).get("partidaAtual").get("id").asText();
    }

    private JsonNode marcarGol(String token, String rachaId, String partidaId, int timeNumero) throws Exception {
        MvcResult gol = mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas/" + partidaId + "/gols")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumero\": %d}".formatted(timeNumero)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(gol);
    }

    private JsonNode encerrarPartida(String rachaId, String partidaId, Integer vencedorTimeNumero) throws Exception {
        var request = post("/api/rachas/" + rachaId + "/partidas/" + partidaId + "/encerrar")
                .header("Authorization", "Bearer " + organizadorToken);
        if (vencedorTimeNumero != null) {
            request = request.contentType(MediaType.APPLICATION_JSON)
                    .content("{\"vencedorTimeNumero\": %d}".formatted(vencedorTimeNumero));
        }
        MvcResult encerrada = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return json(encerrada);
    }

    // ---------- iniciar ----------

    @Test
    @DisplayName("iniciar exige times sorteados, organizador, e congela as mutações do racha")
    void iniciarGuardas() throws Exception {
        JsonNode racha = criarRachaFutsal(2, null);
        String rachaId = racha.get("id").asText();

        // Sem times sorteados → 400 com orientação.
        mockMvc.perform(post("/api/rachas/" + rachaId + "/iniciar")
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(Matchers.containsString("Sorteie")));

        for (int i = 1; i <= 8; i++) {
            adicionarAvulso(rachaId, "Linha " + i);
        }
        mockMvc.perform(post("/api/rachas/" + rachaId + "/sorteio")
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk());

        // Quem não é organizador não inicia.
        mockMvc.perform(post("/api/rachas/" + rachaId + "/iniciar")
                        .header("Authorization", "Bearer " + intrusoToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/rachas/" + rachaId + "/iniciar")
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.iniciadoEm").isNotEmpty())
                .andExpect(jsonPath("$.sugestaoProximaPartida.timeNumeroA").value(1))
                .andExpect(jsonPath("$.sugestaoProximaPartida.timeNumeroB").value(2));

        // EM_ANDAMENTO congela sorteio, participantes e config (times ficam estáveis).
        mockMvc.perform(post("/api/rachas/" + rachaId + "/sorteio")
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/rachas/" + rachaId + "/participantes")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeAvulso\": \"Atrasado\", \"nivelTecnico\": 3}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/rachas/" + rachaId + "/config")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qtdTimes\": 3}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- partidas e gols ----------

    @Test
    @DisplayName("partida: validações de times, uma ativa por vez, gols por qualquer membro, remoção com permissão")
    void partidasEGols() throws Exception {
        String rachaId = prepararRachaAoVivo(2, null);

        // Times iguais e time inexistente → 400.
        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumeroA\": 1, \"timeNumeroB\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(Matchers.containsString("diferentes")));
        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumeroA\": 1, \"timeNumeroB\": 99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(Matchers.containsString("não existe")));

        String partidaId = criarPartida(rachaId, 1, 2, 600);

        // A duração usada vira o prefill da próxima (campo legado do racha).
        mockMvc.perform(get("/api/rachas/" + rachaId + "/ao-vivo")
                        .header("Authorization", "Bearer " + membroToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracaoPartidaSeg").value(600))
                .andExpect(jsonPath("$.partidaAtual.duracaoPrevistaSeg").value(600))
                .andExpect(jsonPath("$.agora").isNotEmpty());

        // Só uma partida ativa por vez.
        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumeroA\": 1, \"timeNumeroB\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(Matchers.containsString("Encerre a partida atual")));

        // Membro (não organizador) marca gol sem autor.
        JsonNode aposGolMembro = marcarGol(membroToken, rachaId, partidaId, 1);
        assertThat(aposGolMembro.get("partidaAtual").get("placarA").asInt()).isEqualTo(1);
        String golDoMembroId = aposGolMembro.get("partidaAtual").get("gols").get(0).get("id").asText();

        // Gol com autor resolve o nome do participante.
        MvcResult detalhe = mockMvc.perform(get("/api/rachas/" + rachaId)
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk())
                .andReturn();
        String participanteId = json(detalhe).get("participantes").get(0).get("id").asText();
        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas/" + partidaId + "/gols")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumero\": 2, \"participanteId\": \"%s\"}".formatted(participanteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partidaAtual.placarB").value(1))
                .andExpect(jsonPath("$.partidaAtual.gols[1].participanteNome").isNotEmpty());

        // Quem não é membro não marca; time fora de quadra não pontua.
        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas/" + partidaId + "/gols")
                        .header("Authorization", "Bearer " + intrusoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumero\": 1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas/" + partidaId + "/gols")
                        .header("Authorization", "Bearer " + membroToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumero\": 3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(Matchers.containsString("não está em quadra")));

        // Remoção: membro não remove gol dos outros; organizador remove qualquer um; autor remove o próprio.
        MvcResult aoVivo = mockMvc.perform(get("/api/rachas/" + rachaId + "/ao-vivo")
                        .header("Authorization", "Bearer " + membroToken))
                .andExpect(status().isOk())
                .andReturn();
        String golDoOrganizadorId = json(aoVivo).get("partidaAtual").get("gols").get(1).get("id").asText();

        mockMvc.perform(delete("/api/rachas/" + rachaId + "/gols/" + golDoOrganizadorId)
                        .header("Authorization", "Bearer " + membroToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/rachas/" + rachaId + "/gols/" + golDoOrganizadorId)
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaAtual.placarB").value(0));
        mockMvc.perform(delete("/api/rachas/" + rachaId + "/gols/" + golDoMembroId)
                        .header("Authorization", "Bearer " + membroToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaAtual.placarA").value(0));
    }

    // ---------- vencedor, incumbente e sugestão ----------

    @Test
    @DisplayName("vencedor fica: placar decide; empates usam o critério do placar (quem fica / quem entra) e a fila")
    void vencedorEmpatesESugestao() throws Exception {
        // 3 times — defaults: 0x0 = TIME_QUE_FICA, com gols = TIME_QUE_ENTRA.
        String rachaId = prepararRachaAoVivo(3, null);

        // Partida 1: time 1 vence no placar; sugestão mantém o 1 e chama o 3 (fila).
        String partida1 = criarPartida(rachaId, 1, 2, null);
        marcarGol(organizadorToken, rachaId, partida1, 1);
        JsonNode aposP1 = encerrarPartida(rachaId, partida1, null);
        assertThat(aposP1.get("partidasEncerradas").get(0).get("vencedorTimeNumero").asInt()).isEqualTo(1);
        assertThat(aposP1.get("partidasEncerradas").get(0).get("ordem").asInt()).isEqualTo(1);
        assertThat(aposP1.get("sugestaoProximaPartida").get("timeNumeroA").asInt()).isEqualTo(1);
        assertThat(aposP1.get("sugestaoProximaPartida").get("timeNumeroB").asInt()).isEqualTo(3);

        // Partida 2 (1 x 3): 0x0 — quem fica (incumbente = time 1) ganha.
        String partida2 = criarPartida(rachaId, 1, 3, null);
        JsonNode aposP2 = encerrarPartida(rachaId, partida2, null);
        assertThat(aposP2.get("partidasEncerradas").get(1).get("vencedorTimeNumero").asInt()).isEqualTo(1);
        assertThat(aposP2.get("sugestaoProximaPartida").get("timeNumeroA").asInt()).isEqualTo(1);
        assertThat(aposP2.get("sugestaoProximaPartida").get("timeNumeroB").asInt()).isEqualTo(2);

        // Partida 3 (1 x 2): 1x1 — quem entra (time 2) ganha.
        String partida3 = criarPartida(rachaId, 1, 2, null);
        marcarGol(organizadorToken, rachaId, partida3, 1);
        marcarGol(membroToken, rachaId, partida3, 2);
        JsonNode aposP3 = encerrarPartida(rachaId, partida3, null);
        assertThat(aposP3.get("partidasEncerradas").get(2).get("vencedorTimeNumero").asInt()).isEqualTo(2);
        assertThat(aposP3.get("partidasEncerradas").get(2).get("ordem").asInt()).isEqualTo(3);
        assertThat(aposP3.get("sugestaoProximaPartida").get("timeNumeroA").asInt()).isEqualTo(2);
        assertThat(aposP3.get("sugestaoProximaPartida").get("timeNumeroB").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("primeira partida empatada não tem incumbente: fica sem vencedor e sem sugestão")
    void primeiraPartidaEmpatadaSemIncumbente() throws Exception {
        String rachaId = prepararRachaAoVivo(2, null);
        String partidaId = criarPartida(rachaId, 1, 2, null);
        JsonNode snapshot = encerrarPartida(rachaId, partidaId, null);
        assertThat(snapshot.get("partidasEncerradas").get(0).get("vencedorTimeNumero").isNull()).isTrue();
        assertThat(snapshot.get("sugestaoProximaPartida").isNull()).isTrue();
    }

    @Test
    @DisplayName("pênaltis: empate com gols aceita o vencedor informado no encerramento")
    void empatePenaltis() throws Exception {
        String rachaId = prepararRachaAoVivo(2, "\"criterioEmpateGols\": \"PENALTIS\"");

        // Critérios persistidos: default no 0x0, pênaltis no empate com gols.
        mockMvc.perform(get("/api/rachas/" + rachaId)
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterioEmpateZero").value("TIME_QUE_FICA"))
                .andExpect(jsonPath("$.criterioEmpateGols").value("PENALTIS"));

        String partidaId = criarPartida(rachaId, 1, 2, null);
        marcarGol(organizadorToken, rachaId, partidaId, 1);
        marcarGol(membroToken, rachaId, partidaId, 2);

        // Vencedor informado precisa estar em quadra.
        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas/" + partidaId + "/encerrar")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vencedorTimeNumero\": 99}"))
                .andExpect(status().isBadRequest());

        JsonNode snapshot = encerrarPartida(rachaId, partidaId, 2);
        assertThat(snapshot.get("partidasEncerradas").get(0).get("vencedorTimeNumero").asInt()).isEqualTo(2);
        assertThat(snapshot.get("partidasEncerradas").get(0).get("placarA").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("ambos saem: empate 0x0 sugere os dois próximos da fila")
    void empateAmbosSaem() throws Exception {
        String rachaId = prepararRachaAoVivo(4, "\"criterioEmpateZero\": \"AMBOS_SAEM\"");
        String partidaId = criarPartida(rachaId, 1, 2, null);
        JsonNode snapshot = encerrarPartida(rachaId, partidaId, null);
        assertThat(snapshot.get("partidasEncerradas").get(0).get("vencedorTimeNumero").isNull()).isTrue();
        assertThat(snapshot.get("sugestaoProximaPartida").get("timeNumeroA").asInt()).isEqualTo(3);
        assertThat(snapshot.get("sugestaoProximaPartida").get("timeNumeroB").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("critérios de empate são editáveis pelo PATCH /config enquanto o racha está aberto")
    void configEditaCriterios() throws Exception {
        JsonNode racha = criarRachaFutsal(2, null);
        String rachaId = racha.get("id").asText();
        assertThat(racha.get("criterioEmpateZero").asText()).isEqualTo("TIME_QUE_FICA");
        assertThat(racha.get("criterioEmpateGols").asText()).isEqualTo("TIME_QUE_ENTRA");

        mockMvc.perform(patch("/api/rachas/" + rachaId + "/config")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"criterioEmpateZero\": \"GOL_DE_OURO\", \"criterioEmpateGols\": \"AMBOS_SAEM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterioEmpateZero").value("GOL_DE_OURO"))
                .andExpect(jsonPath("$.criterioEmpateGols").value("AMBOS_SAEM"));
    }

    // ---------- concluir e histórico ----------

    @Test
    @DisplayName("concluir um racha ao vivo encerra a partida ativa e o histórico continua acessível aos membros")
    void concluirAoVivoPreservaHistorico() throws Exception {
        String rachaId = prepararRachaAoVivo(2, null);
        String partidaId = criarPartida(rachaId, 1, 2, null);
        marcarGol(membroToken, rachaId, partidaId, 1);

        // Membro não conclui; membro também não sai durante o jogo.
        mockMvc.perform(post("/api/rachas/" + rachaId + "/concluir")
                        .header("Authorization", "Bearer " + membroToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/rachas/" + rachaId + "/participantes/me")
                        .header("Authorization", "Bearer " + membroToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/rachas/" + rachaId + "/concluir")
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        // Histórico pós-conclusão: partida encerrada com o gol registrado (feature "histórico do racha").
        mockMvc.perform(get("/api/rachas/" + rachaId + "/ao-vivo")
                        .header("Authorization", "Bearer " + membroToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.partidaAtual").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.partidasEncerradas.length()").value(1))
                .andExpect(jsonPath("$.partidasEncerradas[0].vencedorTimeNumero").value(1))
                .andExpect(jsonPath("$.partidasEncerradas[0].gols.length()").value(1));

        // Depois de concluído, marcar gol não é mais possível.
        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas/" + partidaId + "/gols")
                        .header("Authorization", "Bearer " + membroToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumero\": 1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("racha ao vivo esquecido há mais de 24h é concluído automaticamente pelo cron")
    void expiracaoConcluiEmAndamentoEsquecido() throws Exception {
        String rachaId = prepararRachaAoVivo(2, null);
        criarPartida(rachaId, 1, 2, null);

        Racha racha = rachaRepository.findById(rachaId).orElseThrow();
        racha.setIniciadoEm(LocalDateTime.now().minusHours(25));
        rachaRepository.save(racha);

        int concluidos = rachaExpiracaoService.concluirEmAndamentoEsquecidos();
        assertThat(concluidos).isGreaterThanOrEqualTo(1);

        assertThat(rachaRepository.findById(rachaId).orElseThrow().getStatus())
                .isEqualTo(StatusRachaEnum.CONCLUIDO);
        mockMvc.perform(get("/api/rachas/" + rachaId + "/ao-vivo")
                        .header("Authorization", "Bearer " + membroToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaAtual").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.partidasEncerradas[0].status").value("ENCERRADA"));
    }
}
