package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.enums.SituacaoAtualizacaoEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.gomesdev.sortifyteams.domain.versaoapp.AtualizacaoService.classificar;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Classificação da checagem (spec 002, FR-002) — regra crítica.
 * Cenário-base: publicado versionCode 5, mínimo 3, runtime "2".
 */
class AtualizacaoServiceTest {

    private static final int PUBLICADO = 5;
    private static final int MINIMO = 3;
    private static final String RUNTIME = "2";

    private SituacaoAtualizacaoEnum situacao(int instalado, String runtimeInstalado, boolean conteudoNovo) {
        return classificar(instalado, runtimeInstalado, PUBLICADO, MINIMO, RUNTIME, conteudoNovo);
    }

    @Test
    @DisplayName("mesmo versionCode e sem conteúdo novo: em dia")
    void mesmoVersionCodeEmDia() {
        assertThat(situacao(5, RUNTIME, false)).isEqualTo(SituacaoAtualizacaoEnum.EM_DIA);
    }

    @Test
    @DisplayName("um abaixo do publicado, acima do mínimo: opcional")
    void umAbaixoOpcional() {
        assertThat(situacao(4, RUNTIME, false)).isEqualTo(SituacaoAtualizacaoEnum.RUNTIME_OPCIONAL);
    }

    @Test
    @DisplayName("exatamente no mínimo suportado: opcional, não bloqueia")
    void exatamenteNoMinimoNaoBloqueia() {
        assertThat(situacao(MINIMO, RUNTIME, false)).isEqualTo(SituacaoAtualizacaoEnum.RUNTIME_OPCIONAL);
    }

    @Test
    @DisplayName("um abaixo do mínimo: obrigatório")
    void umAbaixoDoMinimoBloqueia() {
        assertThat(situacao(MINIMO - 1, RUNTIME, false))
                .isEqualTo(SituacaoAtualizacaoEnum.RUNTIME_OBRIGATORIO);
    }

    @Test
    @DisplayName("muito abaixo do mínimo: obrigatório")
    void muitoAbaixoBloqueia() {
        assertThat(situacao(1, RUNTIME, false)).isEqualTo(SituacaoAtualizacaoEnum.RUNTIME_OBRIGATORIO);
    }

    @Test
    @DisplayName("cliente à frente do publicado (build de dev): em dia")
    void clienteAFrenteEmDia() {
        assertThat(situacao(9, RUNTIME, false)).isEqualTo(SituacaoAtualizacaoEnum.EM_DIA);
    }

    @Test
    @DisplayName("conteúdo novo no mesmo runtime: CONTEUDO")
    void conteudoNovoMesmoRuntime() {
        assertThat(situacao(5, RUNTIME, true)).isEqualTo(SituacaoAtualizacaoEnum.CONTEUDO);
    }

    @Test
    @DisplayName("conteúdo novo com runtime divergente não é oferecido (C13)")
    void conteudoNaoSeAplicaARuntimeDiferente() {
        assertThat(situacao(5, "1", true)).isEqualTo(SituacaoAtualizacaoEnum.EM_DIA);
    }

    @Test
    @DisplayName("runtime nulo nunca recebe conteúdo")
    void runtimeNuloNaoRecebeConteudo() {
        assertThat(situacao(5, null, true)).isEqualTo(SituacaoAtualizacaoEnum.EM_DIA);
    }

    @Test
    @DisplayName("bloqueio por mínimo vence conteúdo novo — OTA não resolveria incompatibilidade")
    void bloqueioVenceConteudo() {
        assertThat(situacao(1, RUNTIME, true)).isEqualTo(SituacaoAtualizacaoEnum.RUNTIME_OBRIGATORIO);
    }

    @Test
    @DisplayName("bloqueio por mínimo vence atualização opcional")
    void bloqueioVenceOpcional() {
        assertThat(classificar(2, RUNTIME, 5, 5, RUNTIME, false))
                .isEqualTo(SituacaoAtualizacaoEnum.RUNTIME_OBRIGATORIO);
    }

    @Test
    @DisplayName("mínimo igual ao publicado força todo mundo abaixo a atualizar")
    void minimoIgualAoPublicado() {
        assertThat(classificar(4, RUNTIME, 5, 5, RUNTIME, false))
                .isEqualTo(SituacaoAtualizacaoEnum.RUNTIME_OBRIGATORIO);
        assertThat(classificar(5, RUNTIME, 5, 5, RUNTIME, false))
                .isEqualTo(SituacaoAtualizacaoEnum.EM_DIA);
    }
}
