package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.IntegrationTestBase;
import com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest;
import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Download do APK a partir do banco (spec 002, Fase 1 — T004/T005).
 *
 * <p>Estes testes só têm valor contra um Postgres real: o que se quer provar é
 * o {@code substr} sobre {@code bytea} devolvendo exatamente a faixa pedida —
 * inclusive a conversão entre o {@code Range} 0-based do HTTP e o índice
 * 1-based do SQL.
 *
 * <p><b>Sobre o {@code asyncDispatch}</b>: o controller devolve
 * {@code StreamingResponseBody}, que o Spring MVC processa de forma
 * assíncrona. Sem despachar o resultado assíncrono explicitamente, o MockMvc
 * espera o timeout padrão (30s) e reporta 500 — não é falha de produção, é o
 * protocolo do teste com uma resposta assíncrona real.
 */
@AutoConfigureMockMvc
class ApkDownloadFlowTest extends IntegrationTestBase {

    private static final int TAMANHO = 300_000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VersaoAppService service;

    @Autowired
    private VersaoRuntimeRepository versaoRepository;

    @Autowired
    private VersaoRuntimeArquivoRepository arquivoRepository;

    private byte[] conteudo;
    private VersaoRuntime versao;

    @BeforeEach
    void publicarVersao() {
        arquivoRepository.deleteAll();
        versaoRepository.deleteAll();

        conteudo = new byte[TAMANHO];
        conteudo[0] = 0x50;
        conteudo[1] = 0x4B;
        conteudo[2] = 0x03;
        conteudo[3] = 0x04;
        for (int i = 4; i < TAMANHO; i++) {
            conteudo[i] = (byte) (i % 251); // padrão não repetitivo por byte
        }
        versao = service.publicar(new PublicarVersaoRequest("1.1.0", 2, "1", 1, "teste"),
                new MockMultipartFile("arquivo", "zerinho.apk",
                        "application/vnd.android.package-archive", conteudo),
                PlataformaAppEnum.ANDROID, null);
    }

    /** Executa a requisição e resolve o {@code StreamingResponseBody} assíncrono. */
    private MvcResult baixar(MockHttpServletRequestBuilder requisicao) throws Exception {
        MvcResult iniciado = mockMvc.perform(requisicao)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(iniciado)).andReturn();
    }

    @Test
    @DisplayName("download completo devolve o arquivo idêntico ao publicado")
    void downloadCompleto() throws Exception {
        MvcResult resultado = baixar(get("/api/app/apk"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(resultado.getResponse().getHeader(HttpHeaders.ETAG))
                .isEqualTo("\"" + versao.getSha256() + "\"");
        assertThat(resultado.getResponse().getContentAsByteArray()).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("Range devolve exatamente a faixa pedida — off-by-one 0-based x 1-based")
    void rangeDevolveFaixaExata() throws Exception {
        MvcResult resultado = baixar(get("/api/app/apk").header(HttpHeaders.RANGE, "bytes=1000-1999"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(206);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 1000-1999/" + TAMANHO);
        byte[] parcial = resultado.getResponse().getContentAsByteArray();
        assertThat(parcial).hasSize(1000);
        assertThat(parcial).isEqualTo(Arrays.copyOfRange(conteudo, 1000, 2000));
    }

    @Test
    @DisplayName("primeiro byte do arquivo — o limite onde o off-by-one apareceria")
    void primeiroByte() throws Exception {
        MvcResult resultado = baixar(get("/api/app/apk").header(HttpHeaders.RANGE, "bytes=0-0"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(206);
        byte[] parcial = resultado.getResponse().getContentAsByteArray();
        assertThat(parcial).hasSize(1);
        assertThat(parcial[0]).isEqualTo(conteudo[0]);
    }

    @Test
    @DisplayName("último byte do arquivo")
    void ultimoByte() throws Exception {
        MvcResult resultado = baixar(get("/api/app/apk")
                .header(HttpHeaders.RANGE, "bytes=" + (TAMANHO - 1) + "-"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(206);
        byte[] parcial = resultado.getResponse().getContentAsByteArray();
        assertThat(parcial).hasSize(1);
        assertThat(parcial[0]).isEqualTo(conteudo[TAMANHO - 1]);
    }

    @Test
    @DisplayName("retomada: faixa aberta emenda com o começo e reconstrói o arquivo")
    void retomadaReconstroiArquivo() throws Exception {
        int corte = 123_457; // corte propositalmente fora de múltiplo de fatia
        MvcResult inicioResultado = baixar(get("/api/app/apk")
                .header(HttpHeaders.RANGE, "bytes=0-" + (corte - 1)));
        assertThat(inicioResultado.getResponse().getStatus()).isEqualTo(206);
        byte[] inicio = inicioResultado.getResponse().getContentAsByteArray();

        MvcResult restoResultado = baixar(get("/api/app/apk")
                .header(HttpHeaders.RANGE, "bytes=" + corte + "-"));
        assertThat(restoResultado.getResponse().getStatus()).isEqualTo(206);
        byte[] resto = restoResultado.getResponse().getContentAsByteArray();

        byte[] emendado = new byte[inicio.length + resto.length];
        System.arraycopy(inicio, 0, emendado, 0, inicio.length);
        System.arraycopy(resto, 0, emendado, inicio.length, resto.length);

        assertThat(emendado).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("Range além do arquivo devolve 416")
    void rangeInsatisfazivel() throws Exception {
        // 416 é resposta síncrona (o controller nunca chega a montar o
        // StreamingResponseBody) — não passa por asyncDispatch.
        mockMvc.perform(get("/api/app/apk").header(HttpHeaders.RANGE, "bytes=" + TAMANHO + "-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */" + TAMANHO));
    }

    @Test
    @DisplayName("If-Range divergente devolve 200 com o arquivo inteiro (FR-011)")
    void ifRangeDivergente() throws Exception {
        MvcResult resultado = baixar(get("/api/app/apk")
                .header(HttpHeaders.RANGE, "bytes=1000-1999")
                .header(HttpHeaders.IF_RANGE, "\"hash-de-outra-versao\""));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(resultado.getResponse().getContentAsByteArray()).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("download não exige autenticação (FR-001)")
    void downloadPublico() throws Exception {
        assertThat(baixar(get("/api/app/apk")).getResponse().getStatus()).isEqualTo(200);
        assertThat(baixar(get("/api/app/apk/2")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("caminho legado /downloads/** redireciona para a versão ativa (C24)")
    void downloadLegadoRedireciona() throws Exception {
        mockMvc.perform(get("/downloads/sortify-teams-v1.0.0.apk"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/app/apk"));
    }

    @Test
    @DisplayName("downloads concorrentes servem o arquivo íntegro (FR-029)")
    void downloadsConcorrentes() throws Exception {
        int paralelos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(paralelos);
        try {
            List<Callable<byte[]>> tarefas = java.util.stream.IntStream.range(0, paralelos)
                    .<Callable<byte[]>>mapToObj(i -> () -> baixar(get("/api/app/apk"))
                            .getResponse().getContentAsByteArray())
                    .toList();

            for (Future<byte[]> resultado : pool.invokeAll(tarefas)) {
                assertThat(resultado.get()).isEqualTo(conteudo);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
