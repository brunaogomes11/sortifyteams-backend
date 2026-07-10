package com.gomesdev.sortifyteams.domain.racha.partida;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomesdev.sortifyteams.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Canal STOMP do racha ao vivo: CONNECT exige JWT, SUBSCRIBE exige ser membro,
 * e cada gol publica o snapshot no tópico /topic/rachas/{id}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AoVivoWebSocketTest extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String organizadorToken;
    private String membroToken;
    private String intrusoToken;
    private WebSocketStompClient stompClient;
    private StompSession sessao;

    @BeforeEach
    void prepara() throws Exception {
        int n = SEQ.incrementAndGet();
        organizadorToken = registrar("orgws" + n);
        membroToken = registrar("membrows" + n);
        intrusoToken = registrar("intrusows" + n);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // O broker publica JSON; entregamos o corpo cru como String e parseamos
        // com o ObjectMapper (independe do converter/versão do Jackson no client).
        stompClient.setMessageConverter(new MessageConverter() {
            @Override
            public Object fromMessage(Message<?> message, Class<?> targetClass) {
                return new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);
            }

            @Override
            @Nullable
            public Message<?> toMessage(Object payload, @Nullable MessageHeaders headers) {
                return null;
            }
        });
    }

    @AfterEach
    void desconecta() {
        if (sessao != null && sessao.isConnected()) {
            sessao.disconnect();
        }
        stompClient.stop();
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    private StompSession conectar(String accessToken) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (accessToken != null) {
            connectHeaders.add("Authorization", "Bearer " + accessToken);
        }
        return stompClient.connectAsync(wsUrl(), (WebSocketHttpHeaders) null, connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(10, TimeUnit.SECONDS);
    }

    private BlockingQueue<String> assinar(StompSession sessao, String rachaId) {
        BlockingQueue<String> mensagens = new LinkedBlockingQueue<>();
        sessao.subscribe("/topic/rachas/" + rachaId, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                mensagens.add((String) payload);
            }
        });
        return mensagens;
    }

    // ---------- setup REST (mesmo fluxo do PartidaFlowTest) ----------

    private String registrar(String username) throws Exception {
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Usuario WS", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "JOGADOR"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(registro).get("accessToken").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String esporteId() throws Exception {
        MvcResult esportes = mockMvc.perform(get("/api/esportes")
                        .header("Authorization", "Bearer " + organizadorToken))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode esporte : json(esportes)) {
            if ("Futsal".equals(esporte.get("nome").asText())) {
                return esporte.get("id").asText();
            }
        }
        throw new IllegalStateException("Esporte Futsal não encontrado no seed.");
    }

    /** Racha futsal ao vivo com o membro dentro e uma partida 1x2 rolando; devolve [rachaId, partidaId]. */
    private String[] prepararRachaComPartida() throws Exception {
        MvcResult criado = mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"esporteId\": \"%s\", \"qtdTimes\": 2}".formatted(esporteId())))
                .andExpect(status().isCreated())
                .andReturn();
        String rachaId = json(criado).get("id").asText();
        String tokenConvite = json(criado).get("tokenConvite").asText();

        for (int i = 1; i <= 7; i++) {
            mockMvc.perform(post("/api/rachas/" + rachaId + "/participantes")
                            .header("Authorization", "Bearer " + organizadorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nomeAvulso\": \"Linha %d\", \"nivelTecnico\": 3}".formatted(i)))
                    .andExpect(status().isCreated());
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

        MvcResult partida = mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumeroA\": 1, \"timeNumeroB\": 2}"))
                .andExpect(status().isCreated())
                .andReturn();
        String partidaId = json(partida).get("partidaAtual").get("id").asText();
        return new String[]{rachaId, partidaId};
    }

    // ---------- testes ----------

    @Test
    @DisplayName("CONNECT sem token é recusado com frame ERROR")
    void connectSemTokenFalha() {
        assertThatThrownBy(() -> conectar(null)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("membro assinado recebe o snapshot quando um gol é registrado")
    void membroRecebeSnapshotDoGol() throws Exception {
        String[] ids = prepararRachaComPartida();
        String rachaId = ids[0];
        String partidaId = ids[1];

        sessao = conectar(membroToken);
        BlockingQueue<String> mensagens = assinar(sessao, rachaId);
        Thread.sleep(500); // garante o SUBSCRIBE processado antes do broadcast

        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas/" + partidaId + "/gols")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumero\": 1}"))
                .andExpect(status().isCreated());

        String mensagem = mensagens.poll(5, TimeUnit.SECONDS);
        assertThat(mensagem).isNotNull();
        JsonNode snapshot = objectMapper.readTree(mensagem);
        assertThat(snapshot.get("rachaId").asText()).isEqualTo(rachaId);
        assertThat(snapshot.get("partidaAtual").get("placarA").asInt()).isEqualTo(1);
        assertThat(snapshot.get("agora").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("quem não é membro não recebe nada do tópico (SUBSCRIBE negado)")
    void naoMembroNaoRecebe() throws Exception {
        String[] ids = prepararRachaComPartida();
        String rachaId = ids[0];
        String partidaId = ids[1];

        sessao = conectar(intrusoToken);
        BlockingQueue<String> mensagens = assinar(sessao, rachaId);
        Thread.sleep(500);

        mockMvc.perform(post("/api/rachas/" + rachaId + "/partidas/" + partidaId + "/gols")
                        .header("Authorization", "Bearer " + organizadorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeNumero\": 1}"))
                .andExpect(status().isCreated());

        assertThat(mensagens.poll(2, TimeUnit.SECONDS)).isNull();
    }
}
