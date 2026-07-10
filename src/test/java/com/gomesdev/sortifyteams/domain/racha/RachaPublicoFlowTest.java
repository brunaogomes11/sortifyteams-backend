package com.gomesdev.sortifyteams.domain.racha;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Feed de rachas públicos: proximidade por GPS e fallback por cidade.
 *
 * <p>O feed é global e o banco é compartilhado entre os testes da classe, então
 * cada teste usa coordenadas/cidades exclusivas (com {@code seq}) e um raio para
 * se isolar dos rachas criados pelos demais.</p>
 */
@AutoConfigureMockMvc
class RachaPublicoFlowTest extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RachaRepository rachaRepository;

    @Autowired
    private com.gomesdev.sortifyteams.domain.quadra.QuadraRepository quadraRepository;

    @Autowired
    private com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository usuarioRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;
    private int seq;

    @BeforeEach
    void registraOrganizador() throws Exception {
        seq = SEQ.incrementAndGet();
        String username = "publico-org" + seq;
        MvcResult registro = mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Ana Organizadora", "username": "%s",
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

    /** Cria um racha público e injeta coordenadas/cidade (o geocoding fica off em teste). */
    private String criarRachaPublico(double lat, double lon, String cidade) throws Exception {
        MvcResult criado = mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "local": "Quadra do Zé", "publico": true}
                                """.formatted(esporteId("Futsal"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publico").value(true))
                .andReturn();
        String id = objectMapper.readTree(criado.getResponse().getContentAsString()).get("id").asText();

        Racha racha = rachaRepository.findById(id).orElseThrow();
        racha.setLatitude(lat);
        racha.setLongitude(lon);
        racha.setCidade(cidade);
        rachaRepository.save(racha);
        return id;
    }

    @Test
    @DisplayName("por GPS: dentro do raio, lista o racha com distanciaKm preenchida")
    void listaPorProximidade() throws Exception {
        // Região exclusiva deste teste (lat/lon ~ seq) + raio pequeno isola dos demais.
        double lat = 10.0 + seq;
        double lon = 10.0 + seq;
        criarRachaPublico(lat + 0.01, lon + 0.01, "Cidade Gps " + seq);

        mockMvc.perform(get("/api/rachas/publicos")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("lat", String.valueOf(lat))
                        .param("lon", String.valueOf(lon))
                        .param("raioKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].esporteNome").value("Futsal"))
                .andExpect(jsonPath("$[0].local").value("Quadra do Zé"))
                .andExpect(jsonPath("$[0].organizador").value("Ana"))
                .andExpect(jsonPath("$[0].distanciaKm").isNumber())
                .andExpect(jsonPath("$[0].souMembro").value(true));
    }

    @Test
    @DisplayName("por cidade: filtra pela cidade informada e não traz distância (fallback sem GPS)")
    void listaPorCidade() throws Exception {
        String cidadeSp = "Cidade Filtro " + seq;
        criarRachaPublico(-23.5610, -46.6560, cidadeSp);
        criarRachaPublico(-22.9068, -43.1729, "Outra Cidade " + seq);

        mockMvc.perform(get("/api/rachas/publicos")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("cidade", cidadeSp.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cidade").value(cidadeSp))
                .andExpect(jsonPath("$[0].distanciaKm").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("sem GPS e sem cidade responde 400 obrigando o filtro")
    void semLocalizacaoNemCidadeResponde400() throws Exception {
        mockMvc.perform(get("/api/rachas/publicos")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message[0]").value(org.hamcrest.Matchers.containsString("cidade")));
    }

    @Test
    @DisplayName("racha público herda as coordenadas da quadra ao reservar e aparece no feed por GPS (FIX 1)")
    void rachaPublicoHerdaCoordenadasDaQuadraNaReserva() throws Exception {
        // Dono aprovado com quadra georreferenciada (coords injetadas — geocoding off em teste).
        String usernameDono = "publico-dono" + seq;
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto": "Dono Publico", "username": "%s",
                                 "email": "%s@teste.com", "senha": "senha123", "role": "DONO_QUADRA"}
                                """.formatted(usernameDono, usernameDono)))
                .andExpect(status().isCreated());
        var dono = usuarioRepository.findByUsername(usernameDono).orElseThrow();
        dono.setStatus(com.gomesdev.sortifyteams.enums.StatusUsuarioEnum.APROVADO);
        usuarioRepository.save(dono);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"senha\": \"senha123\"}".formatted(usernameDono)))
                .andExpect(status().isOk())
                .andReturn();
        String tokenDono = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult quadraCriada = mockMvc.perform(post("/api/dono/quadras")
                        .header("Authorization", "Bearer " + tokenDono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Arena Geo %d", "endereco": "Rua Geo, 1", "contato": "(34) 90000-0000"}
                                """.formatted(seq)))
                .andExpect(status().isCreated())
                .andReturn();
        String quadraId = objectMapper.readTree(quadraCriada.getResponse().getContentAsString())
                .get("id").asText();
        double lat = 40.0 + seq;
        double lon = 40.0 + seq;
        var quadra = quadraRepository.findById(quadraId).orElseThrow();
        quadra.setLatitude(lat);
        quadra.setLongitude(lon);
        quadra.setCidade("Cidade Quadra " + seq);
        quadraRepository.save(quadra);

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

        // Racha público sem localização resolvida: invisível na busca por enquanto.
        MvcResult rachaCriado = mockMvc.perform(post("/api/rachas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"esporteId": "%s", "publico": true}
                                """.formatted(esporteId("Futsal"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.localizacaoResolvida").value(false))
                .andReturn();
        String rachaId = objectMapper.readTree(rachaCriado.getResponse().getContentAsString())
                .get("id").asText();

        java.time.LocalDate proximaSegunda = java.time.LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quadraId": "%s", "rachaId": "%s", "data": "%s", "quadraHorarioIds": ["%s"]}
                                """.formatted(quadraId, rachaId, proximaSegunda, slotId)))
                .andExpect(status().isCreated());

        // Com a reserva, o racha herdou as coordenadas da quadra e ficou visível por GPS.
        mockMvc.perform(get("/api/rachas/" + rachaId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localizacaoResolvida").value(true));
        mockMvc.perform(get("/api/rachas/publicos")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("lat", String.valueOf(lat))
                        .param("lon", String.valueOf(lon))
                        .param("raioKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(rachaId));
    }

    @Test
    @DisplayName("lista as cidades com rachas públicos para o filtro")
    void listaCidadesDisponiveis() throws Exception {
        String cidade = "Cidade Listavel " + seq;
        criarRachaPublico(-23.5610, -46.6560, cidade);

        mockMvc.perform(get("/api/rachas/publicos/cidades")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem(cidade)));
    }
}
