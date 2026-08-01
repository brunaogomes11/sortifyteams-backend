package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.IntegrationTestBase;
import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Publicação de versões do app (spec 002, Fase 0 — T002/T003).
 * Cobre o que o teste unitário não alcança: atomicidade real no banco, a
 * coluna binária fora do JPA e a proteção da cadeia /admin/**.
 *
 * <p>Os testes de transação/atomicidade usam
 * {@link VersaoAppService#publicarComMetadadosExplicitos} — o que se quer
 * provar aqui é o banco (uma ativa, órfão, rollback), não a leitura do
 * manifesto (isso é {@link ApkManifestReaderTest}). Só
 * {@link #publicaPeloPainel()} passa pelo caminho normal, com o APK-fixture
 * mínimo real (~1 KB, {@code fixtures/apk-teste-fixture.apk}) — é o único
 * teste que precisa de um zip com {@code AndroidManifest.xml} de verdade.
 */
@AutoConfigureMockMvc
class VersaoAppFlowTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VersaoAppService service;

    @Autowired
    private VersaoRuntimeRepository versaoRepository;

    @Autowired
    private VersaoRuntimeArquivoRepository arquivoRepository;

    @Autowired
    private ApkBinarioRepository binarioRepository;

    @BeforeEach
    void limpar() {
        arquivoRepository.deleteAll();
        versaoRepository.deleteAll();
    }

    private byte[] apkFalso(int tamanho) {
        byte[] conteudo = new byte[tamanho];
        conteudo[0] = 0x50;
        conteudo[1] = 0x4B;
        conteudo[2] = 0x03;
        conteudo[3] = 0x04;
        for (int i = 4; i < tamanho; i++) {
            conteudo[i] = (byte) (i % 251);
        }
        return conteudo;
    }

    private MockMultipartFile arquivo(byte[] conteudo) {
        return new MockMultipartFile("arquivo", "zerinho.apk",
                "application/vnd.android.package-archive", conteudo);
    }

    private MockMultipartFile apkFixtureReal() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/apk-teste-fixture.apk")) {
            return new MockMultipartFile("arquivo", "zerinho.apk",
                    "application/vnd.android.package-archive", in.readAllBytes());
        }
    }

    @Test
    @DisplayName("publica gravando metadados e binário na mesma transação (C22)")
    void publicaMetadadosEBinario() {
        byte[] conteudo = apkFalso(4096);

        VersaoRuntime versao = service.publicarComMetadadosExplicitos(
                FonteApk.de(arquivo(conteudo)), "1.1.0", 2, "1", 1, "primeira",
                PlataformaAppEnum.ANDROID, null);

        assertThat(versao.getId()).isNotNull();
        assertThat(versao.getTamanhoBytes()).isEqualTo(conteudo.length);
        assertThat(binarioRepository.existeBinario(versao.getId())).isTrue();
        // O binário chegou inteiro na coluna que vive fora do mapeamento JPA.
        assertThat(binarioRepository.tamanhoGravado(versao.getId()))
                .isEqualTo((long) conteudo.length);
    }

    @Test
    @DisplayName("segunda publicação desativa a primeira — uma só ativa por plataforma")
    void apenasUmaAtiva() {
        service.publicarComMetadadosExplicitos(FonteApk.de(arquivo(apkFalso(512))),
                "1.0.0", 1, "1", 1, null, PlataformaAppEnum.ANDROID, null);
        service.publicarComMetadadosExplicitos(FonteApk.de(arquivo(apkFalso(512))),
                "1.1.0", 2, "1", 1, null, PlataformaAppEnum.ANDROID, null);

        List<VersaoRuntime> ativas = versaoRepository
                .findByPlataformaOrderByVersionCodeDesc(PlataformaAppEnum.ANDROID)
                .stream().filter(VersaoRuntime::isAtiva).toList();

        assertThat(ativas).hasSize(1);
        assertThat(ativas.get(0).getVersionCode()).isEqualTo(2);
    }

    @Test
    @DisplayName("publicação recusada não deixa metadado nem binário órfão")
    void publicacaoRecusadaNaoDeixaOrfao() {
        service.publicarComMetadadosExplicitos(FonteApk.de(arquivo(apkFalso(512))),
                "1.0.0", 5, "1", 1, null, PlataformaAppEnum.ANDROID, null);

        try {
            service.publicarComMetadadosExplicitos(FonteApk.de(arquivo(apkFalso(512))),
                    "0.9.0", 4, "1", 1, null, PlataformaAppEnum.ANDROID, null);
        } catch (IllegalArgumentException esperado) {
            // regressão de versionCode — recusada antes de qualquer escrita
        }

        assertThat(versaoRepository.count()).isEqualTo(1);
        assertThat(arquivoRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("/admin/versoes exige sessão de ADMIN")
    void painelExigeAdmin() throws Exception {
        mockMvc.perform(get("/admin/versoes"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/admin/versoes").with(user("alguem").roles("JOGADOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/versoes").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("publicação pelo painel lê versionCode/versao/runtime do APK e redireciona")
    void publicaPeloPainel() throws Exception {
        mockMvc.perform(multipart("/admin/versoes")
                        .file(apkFixtureReal())
                        .param("versionCodeMinimo", "1")
                        .param("notas", "publicada pelo painel")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/versoes"));

        assertThat(versaoRepository.findByPlataformaAndAtivaTrue(PlataformaAppEnum.ANDROID))
                .isPresent().get()
                .satisfies(v -> {
                    assertThat(v.getVersionCode()).isEqualTo(7);
                    assertThat(v.getVersao()).isEqualTo("9.9.9");
                    assertThat(v.getRuntimeVersion()).isEqualTo("42");
                });
    }

    @Test
    @DisplayName("ativar versão anterior devolve o ativo para ela (rollback C14)")
    void rollbackParaVersaoAnterior() {
        VersaoRuntime primeira = service.publicarComMetadadosExplicitos(
                FonteApk.de(arquivo(apkFalso(512))), "1.0.0", 1, "1", 1, null,
                PlataformaAppEnum.ANDROID, null);
        service.publicarComMetadadosExplicitos(FonteApk.de(arquivo(apkFalso(512))),
                "1.1.0", 2, "1", 1, null, PlataformaAppEnum.ANDROID, null);

        service.ativar(primeira.getId());

        assertThat(versaoRepository.findByPlataformaAndAtivaTrue(PlataformaAppEnum.ANDROID))
                .get()
                .extracting(VersaoRuntime::getVersionCode)
                .isEqualTo(1);
    }
}
