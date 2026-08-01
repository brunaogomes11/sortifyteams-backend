package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest;
import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validações de publicação (spec 002, FR-017) — regra crítica, testes na
 * mesma tarefa da implementação (Constituição IV).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VersaoAppServiceTest {

    private static final byte[] CABECALHO_ZIP = {0x50, 0x4B, 0x03, 0x04};

    @Mock
    private VersaoRuntimeRepository versaoRepository;
    @Mock
    private VersaoRuntimeArquivoRepository arquivoRepository;
    @Mock
    private ApkBinarioRepository binarioRepository;

    @InjectMocks
    private VersaoAppService service;

    @BeforeEach
    void semVersaoPublicada() {
        when(versaoRepository.maiorVersionCode(any())).thenReturn(Optional.empty());
        // O dublê precisa simular @PrePersist: é ele que atribui o ULID, e o
        // serviço usa o id logo em seguida para gravar o binário. Sem isto o
        // mock devolveria a entidade sem id — infiel ao que o JPA faz.
        when(versaoRepository.save(any())).thenAnswer(inv -> {
            VersaoRuntime v = inv.getArgument(0);
            v.prePersist();
            return v;
        });
        when(arquivoRepository.save(any())).thenAnswer(inv -> {
            VersaoRuntimeArquivo a = inv.getArgument(0);
            a.prePersist();
            return a;
        });
    }

    private MockMultipartFile apk(String nome, byte[] conteudo) {
        return new MockMultipartFile("arquivo", nome, "application/vnd.android.package-archive", conteudo);
    }

    private MockMultipartFile apkValido() {
        byte[] conteudo = new byte[64];
        System.arraycopy(CABECALHO_ZIP, 0, conteudo, 0, CABECALHO_ZIP.length);
        return apk("zerinho-1.1.0.apk", conteudo);
    }

    private PublicarVersaoRequest pedido(int versionCode, int minimo) {
        return new PublicarVersaoRequest("1.1.0", versionCode, "1", minimo, "notas");
    }

    @Test
    @DisplayName("publica versão válida, ativa a nova e desativa as anteriores")
    void publicaVersaoValida() {
        VersaoRuntime publicada = service.publicar(pedido(2, 1), apkValido(),
                PlataformaAppEnum.ANDROID, "admin-id");

        assertThat(publicada.isAtiva()).isTrue();
        assertThat(publicada.getVersionCode()).isEqualTo(2);
        assertThat(publicada.getTamanhoBytes()).isEqualTo(64);
        assertThat(publicada.getSha256()).hasSize(64);
        assertThat(publicada.getMd5()).hasSize(32);
        verify(versaoRepository).desativarTodas(PlataformaAppEnum.ANDROID);
        verify(binarioRepository).gravar(anyString(), any(InputStream.class), anyLong());
    }

    @Test
    @DisplayName("recusa versionCode igual ao já publicado (C3)")
    void recusaVersionCodeIgual() {
        when(versaoRepository.maiorVersionCode(any())).thenReturn(Optional.of(2));

        assertThatThrownBy(() -> service.publicar(pedido(2, 1), apkValido(),
                PlataformaAppEnum.ANDROID, "admin-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor ou igual");

        verify(binarioRepository, never()).gravar(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("recusa versionCode menor que o já publicado (C3)")
    void recusaVersionCodeMenor() {
        when(versaoRepository.maiorVersionCode(any())).thenReturn(Optional.of(5));

        assertThatThrownBy(() -> service.publicar(pedido(4, 1), apkValido(),
                PlataformaAppEnum.ANDROID, "admin-id"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(versaoRepository, never()).desativarTodas(any());
    }

    @Test
    @DisplayName("aceita versionCode imediatamente acima do publicado")
    void aceitaVersionCodeAcima() {
        when(versaoRepository.maiorVersionCode(any())).thenReturn(Optional.of(2));

        VersaoRuntime publicada = service.publicar(pedido(3, 1), apkValido(),
                PlataformaAppEnum.ANDROID, "admin-id");

        assertThat(publicada.getVersionCode()).isEqualTo(3);
    }

    @Test
    @DisplayName("recusa mínimo suportado maior que a própria versão")
    void recusaMinimoIncoerente() {
        assertThatThrownBy(() -> service.publicar(pedido(3, 4), apkValido(),
                PlataformaAppEnum.ANDROID, "admin-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mínimo suportado");
    }

    @Test
    @DisplayName("aceita mínimo suportado igual à própria versão (limite)")
    void aceitaMinimoIgual() {
        VersaoRuntime publicada = service.publicar(pedido(3, 3), apkValido(),
                PlataformaAppEnum.ANDROID, "admin-id");

        assertThat(publicada.getVersionCodeMinimo()).isEqualTo(3);
    }

    @Test
    @DisplayName("recusa arquivo que não termina em .apk")
    void recusaExtensaoErrada() {
        byte[] conteudo = new byte[8];
        System.arraycopy(CABECALHO_ZIP, 0, conteudo, 0, CABECALHO_ZIP.length);

        assertThatThrownBy(() -> service.publicar(pedido(2, 1), apk("app.zip", conteudo),
                PlataformaAppEnum.ANDROID, "admin-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("(.apk)");
    }

    @Test
    @DisplayName("recusa arquivo com nome de APK mas conteúdo que não é zip")
    void recusaConteudoNaoZip() {
        assertThatThrownBy(() -> service.publicar(pedido(2, 1),
                apk("falso.apk", "isto nao e um apk".getBytes()),
                PlataformaAppEnum.ANDROID, "admin-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assinatura de conteúdo");
    }

    @Test
    @DisplayName("recusa arquivo vazio")
    void recusaArquivoVazio() {
        assertThatThrownBy(() -> service.publicar(pedido(2, 1), apk("vazio.apk", new byte[0]),
                PlataformaAppEnum.ANDROID, "admin-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Envie o arquivo");
    }

    @Test
    @DisplayName("nenhuma escrita acontece quando a validação falha")
    void naoEscreveNadaEmValidacaoQueFalha() {
        assertThatThrownBy(() -> service.publicar(pedido(2, 9), apkValido(),
                PlataformaAppEnum.ANDROID, "admin-id"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(versaoRepository, never()).save(any());
        verify(arquivoRepository, never()).save(any());
        verify(binarioRepository, never()).gravar(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("hash e tamanho gravados correspondem ao conteúdo enviado")
    void hashesCorrespondemAoConteudo() {
        byte[] conteudo = new byte[1024];
        System.arraycopy(CABECALHO_ZIP, 0, conteudo, 0, CABECALHO_ZIP.length);
        for (int i = 4; i < conteudo.length; i++) {
            conteudo[i] = (byte) (i % 251);
        }

        VersaoRuntime publicada = service.publicar(pedido(2, 1), apk("zerinho.apk", conteudo),
                PlataformaAppEnum.ANDROID, "admin-id");

        assertThat(publicada.getTamanhoBytes()).isEqualTo(conteudo.length);
        assertThat(publicada.getSha256()).isEqualTo(hexSha256(conteudo));
    }

    @Test
    @DisplayName("o stream entregue ao repositório contém o arquivo inteiro")
    void streamGravadoContemArquivoInteiro() throws IOException {
        byte[] conteudo = new byte[512];
        System.arraycopy(CABECALHO_ZIP, 0, conteudo, 0, CABECALHO_ZIP.length);

        service.publicar(pedido(2, 1), apk("zerinho.apk", conteudo),
                PlataformaAppEnum.ANDROID, "admin-id");

        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(binarioRepository).gravar(anyString(), captor.capture(), anyLong());

        ByteArrayOutputStream lido = new ByteArrayOutputStream();
        captor.getValue().transferTo(lido);
        assertThat(lido.toByteArray()).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("ativar versão sem binário é recusado (FR-031)")
    void naoAtivaVersaoExpurgada() {
        VersaoRuntime expurgada = new VersaoRuntime(PlataformaAppEnum.ANDROID, "1.0.0", 1,
                "1", null, 1, "admin-id");
        when(versaoRepository.findById("v1")).thenReturn(Optional.of(expurgada));
        when(binarioRepository.existeBinario(any())).thenReturn(false);

        assertThatThrownBy(() -> service.ativar("v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não tem mais o binário");

        verify(versaoRepository, never()).desativarTodas(any());
    }

    @Test
    @DisplayName("ativar versão com binário funciona (rollback da C14)")
    void ativaVersaoComBinario() {
        VersaoRuntime anterior = new VersaoRuntime(PlataformaAppEnum.ANDROID, "1.0.0", 1,
                "1", null, 1, "admin-id");
        when(versaoRepository.findById("v1")).thenReturn(Optional.of(anterior));
        when(binarioRepository.existeBinario(any())).thenReturn(true);

        VersaoRuntime ativada = service.ativar("v1");

        assertThat(ativada.isAtiva()).isTrue();
        verify(versaoRepository).desativarTodas(PlataformaAppEnum.ANDROID);
    }

    private String hexSha256(byte[] conteudo) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
