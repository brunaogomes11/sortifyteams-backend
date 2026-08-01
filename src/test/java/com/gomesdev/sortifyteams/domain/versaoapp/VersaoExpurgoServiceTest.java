package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Política de expurgo (spec 002, C23/FR-030) — regra crítica.
 * O que não pode acontecer nunca: perder o binário da versão ativa, perder o
 * da anterior (que é o alvo do rollback) ou apagar registro de versão.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VersaoExpurgoServiceTest {

    @Mock
    private VersaoRuntimeRepository versaoRepository;
    @Mock
    private VersaoRuntimeArquivoRepository arquivoRepository;
    @Mock
    private ApkBinarioRepository binarioRepository;

    private VersaoExpurgoService service;
    private final LocalDateTime agora = LocalDateTime.now();
    private final LocalDateTime limite = agora.minusDays(30);

    @BeforeEach
    void criar() {
        service = new VersaoExpurgoService(versaoRepository, arquivoRepository,
                binarioRepository, 30);
        when(versaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private VersaoRuntime versao(int versionCode, boolean ativa, int diasAtras) {
        VersaoRuntime v = new VersaoRuntime(PlataformaAppEnum.ANDROID, "1." + versionCode + ".0",
                versionCode, "1", null, 1, null);
        v.prePersist();
        v.setAtiva(ativa);
        v.setPublicadaEm(agora.minusDays(diasAtras));
        return v;
    }

    /** Repositório devolve em ordem decrescente de versionCode. */
    private void publicadas(VersaoRuntime... versoes) {
        List<VersaoRuntime> lista = new ArrayList<>(List.of(versoes));
        lista.sort((a, b) -> Integer.compare(b.getVersionCode(), a.getVersionCode()));
        when(versaoRepository.findByPlataformaOrderByVersionCodeDesc(any())).thenReturn(lista);
    }

    @Test
    @DisplayName("preserva ativa e anterior, expurga as demais antigas")
    void preservaAtivaEAnterior() {
        VersaoRuntime v5 = versao(5, true, 60);
        VersaoRuntime v4 = versao(4, false, 90);
        VersaoRuntime v3 = versao(3, false, 120);
        VersaoRuntime v2 = versao(2, false, 200);
        publicadas(v5, v4, v3, v2);

        int removidos = service.expurgar(PlataformaAppEnum.ANDROID, limite);

        assertThat(removidos).isEqualTo(2);
        verify(binarioRepository).apagarBinario(v3.getId());
        verify(binarioRepository).apagarBinario(v2.getId());
        verify(binarioRepository, never()).apagarBinario(v5.getId());
        verify(binarioRepository, never()).apagarBinario(v4.getId());
        assertThat(v5.isSomenteHistorico()).isFalse();
        assertThat(v4.isSomenteHistorico()).isFalse();
        assertThat(v3.isSomenteHistorico()).isTrue();
    }

    @Test
    @DisplayName("versão ativa nunca perde o binário, mesmo antiquíssima")
    void ativaNuncaPerdeBinario() {
        VersaoRuntime ativa = versao(1, true, 999);
        publicadas(ativa);

        int removidos = service.expurgar(PlataformaAppEnum.ANDROID, limite);

        assertThat(removidos).isZero();
        verify(binarioRepository, never()).apagarBinario(any());
    }

    @Test
    @DisplayName("versões dentro da janela de 30 dias são preservadas")
    void dentroDaJanelaPreservada() {
        VersaoRuntime v5 = versao(5, true, 1);
        VersaoRuntime v4 = versao(4, false, 2);
        VersaoRuntime v3 = versao(3, false, 10);
        publicadas(v5, v4, v3);

        int removidos = service.expurgar(PlataformaAppEnum.ANDROID, limite);

        assertThat(removidos).isZero();
        verify(binarioRepository, never()).apagarBinario(v3.getId());
    }

    @Test
    @DisplayName("expurgo nunca apaga o registro da versão — só o binário")
    void nuncaApagaRegistroDeVersao() {
        publicadas(versao(5, true, 60), versao(4, false, 90), versao(3, false, 120));

        service.expurgar(PlataformaAppEnum.ANDROID, limite);

        verify(versaoRepository, never()).delete(any());
        verify(versaoRepository, never()).deleteAll();
    }

    @Test
    @DisplayName("após rollback, preserva a ativa de versionCode menor e a mais nova")
    void aposRollbackPreservaAsDuasCertas() {
        // A ativa é a 4 (voltaram atrás); a 5 continua sendo a mais nova.
        VersaoRuntime v5 = versao(5, false, 60);
        VersaoRuntime v4 = versao(4, true, 90);
        VersaoRuntime v3 = versao(3, false, 120);
        publicadas(v5, v4, v3);

        int removidos = service.expurgar(PlataformaAppEnum.ANDROID, limite);

        assertThat(removidos).isEqualTo(1);
        verify(binarioRepository, never()).apagarBinario(v4.getId());
        verify(binarioRepository, never()).apagarBinario(v5.getId());
        verify(binarioRepository).apagarBinario(v3.getId());
    }

    @Test
    @DisplayName("versão já expurgada não é reprocessada")
    void jaExpurgadaNaoReprocessa() {
        VersaoRuntime v5 = versao(5, true, 60);
        VersaoRuntime v4 = versao(4, false, 90);
        VersaoRuntime v3 = versao(3, false, 120);
        v3.setBinarioExpurgadoEm(agora.minusDays(5));
        publicadas(v5, v4, v3);

        int removidos = service.expurgar(PlataformaAppEnum.ANDROID, limite);

        assertThat(removidos).isZero();
        verify(binarioRepository, never()).apagarBinario(v3.getId());
    }

    @Test
    @DisplayName("com uma só versão publicada nada é expurgado")
    void umaSoVersao() {
        publicadas(versao(1, true, 400));

        assertThat(service.expurgar(PlataformaAppEnum.ANDROID, limite)).isZero();
    }

    @Test
    @DisplayName("sem versões publicadas o job não faz nada")
    void semVersoes() {
        when(versaoRepository.findByPlataformaOrderByVersionCodeDesc(any())).thenReturn(List.of());

        assertThat(service.expurgar(PlataformaAppEnum.ANDROID, limite)).isZero();
    }
}
