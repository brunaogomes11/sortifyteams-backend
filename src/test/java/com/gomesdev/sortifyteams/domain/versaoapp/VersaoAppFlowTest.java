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

    @Test
    @DisplayName("publica gravando metadados e binário na mesma transação (C22)")
    void publicaMetadadosEBinario() {
        byte[] conteudo = apkFalso(4096);

        VersaoRuntime versao = service.publicar(
                new com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest(
                        "1.1.0", 2, "1", 1, "primeira"),
                arquivo(conteudo), PlataformaAppEnum.ANDROID, null);

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
        service.publicar(new com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest(
                "1.0.0", 1, "1", 1, null), arquivo(apkFalso(512)), PlataformaAppEnum.ANDROID, null);
        service.publicar(new com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest(
                "1.1.0", 2, "1", 1, null), arquivo(apkFalso(512)), PlataformaAppEnum.ANDROID, null);

        List<VersaoRuntime> ativas = versaoRepository
                .findByPlataformaOrderByVersionCodeDesc(PlataformaAppEnum.ANDROID)
                .stream().filter(VersaoRuntime::isAtiva).toList();

        assertThat(ativas).hasSize(1);
        assertThat(ativas.get(0).getVersionCode()).isEqualTo(2);
    }

    @Test
    @DisplayName("publicação recusada não deixa metadado nem binário órfão")
    void publicacaoRecusadaNaoDeixaOrfao() {
        service.publicar(new com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest(
                "1.0.0", 5, "1", 1, null), arquivo(apkFalso(512)), PlataformaAppEnum.ANDROID, null);

        try {
            service.publicar(new com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest(
                    "0.9.0", 4, "1", 1, null), arquivo(apkFalso(512)), PlataformaAppEnum.ANDROID, null);
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
    @DisplayName("publicação pelo painel grava e redireciona")
    void publicaPeloPainel() throws Exception {
        mockMvc.perform(multipart("/admin/versoes")
                        .file(arquivo(apkFalso(2048)))
                        .param("versao", "1.2.0")
                        .param("versionCode", "3")
                        .param("runtimeVersion", "1")
                        .param("versionCodeMinimo", "1")
                        .param("notas", "publicada pelo painel")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/versoes"));

        assertThat(versaoRepository.findByPlataformaAndAtivaTrue(PlataformaAppEnum.ANDROID))
                .isPresent()
                .get()
                .extracting(VersaoRuntime::getVersionCode)
                .isEqualTo(3);
    }

    @Test
    @DisplayName("ativar versão anterior devolve o ativo para ela (rollback C14)")
    void rollbackParaVersaoAnterior() {
        VersaoRuntime primeira = service.publicar(
                new com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest(
                        "1.0.0", 1, "1", 1, null), arquivo(apkFalso(512)), PlataformaAppEnum.ANDROID, null);
        service.publicar(new com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest(
                "1.1.0", 2, "1", 1, null), arquivo(apkFalso(512)), PlataformaAppEnum.ANDROID, null);

        service.ativar(primeira.getId());

        assertThat(versaoRepository.findByPlataformaAndAtivaTrue(PlataformaAppEnum.ANDROID))
                .get()
                .extracting(VersaoRuntime::getVersionCode)
                .isEqualTo(1);
    }
}
