package com.gomesdev.sortifyteams.domain.versaoapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Resolução de {@code Range} (spec 002, D3 — FR-010/FR-011). Regra crítica:
 * é o que decide se a retomada continua do ponto certo ou corrompe o arquivo.
 */
class ApkStreamServiceTest {

    private static final String ETAG = "\"abc123\"";
    private static final long TOTAL = 1000;

    private final ApkStreamService service = new ApkStreamService(mock(ApkBinarioRepository.class));

    private ApkStreamService.Faixa resolver(String range) {
        return service.resolverFaixa(range, null, ETAG, TOTAL);
    }

    @Test
    @DisplayName("sem Range serve o arquivo inteiro")
    void semRangeServeTudo() {
        ApkStreamService.Faixa faixa = resolver(null);

        assertThat(faixa.parcial()).isFalse();
        assertThat(faixa.inicio()).isZero();
        assertThat(faixa.tamanho()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("bytes=0-1023 pede 1024 bytes a partir do offset 0 (limite 0-based x 1-based)")
    void faixaDoInicio() {
        ApkStreamService.Faixa faixa = service.resolverFaixa("bytes=0-1023", null, ETAG, 4096);

        assertThat(faixa.inicio()).isZero();
        assertThat(faixa.fim()).isEqualTo(1023);
        assertThat(faixa.tamanho()).isEqualTo(1024);
    }

    @Test
    @DisplayName("faixa no meio do arquivo")
    void faixaNoMeio() {
        ApkStreamService.Faixa faixa = resolver("bytes=400-499");

        assertThat(faixa.parcial()).isTrue();
        assertThat(faixa.inicio()).isEqualTo(400);
        assertThat(faixa.fim()).isEqualTo(499);
        assertThat(faixa.tamanho()).isEqualTo(100);
    }

    @Test
    @DisplayName("faixa aberta (bytes=N-) vai até o fim — é a forma da retomada")
    void faixaAberta() {
        ApkStreamService.Faixa faixa = resolver("bytes=430-");

        assertThat(faixa.inicio()).isEqualTo(430);
        assertThat(faixa.fim()).isEqualTo(TOTAL - 1);
        assertThat(faixa.tamanho()).isEqualTo(TOTAL - 430);
    }

    @Test
    @DisplayName("sufixo (bytes=-100) devolve os últimos 100 bytes")
    void faixaSufixo() {
        ApkStreamService.Faixa faixa = resolver("bytes=-100");

        assertThat(faixa.inicio()).isEqualTo(900);
        assertThat(faixa.fim()).isEqualTo(999);
        assertThat(faixa.tamanho()).isEqualTo(100);
    }

    @Test
    @DisplayName("último byte é faixa válida de 1 byte")
    void ultimoByte() {
        ApkStreamService.Faixa faixa = resolver("bytes=999-999");

        assertThat(faixa.tamanho()).isEqualTo(1);
        assertThat(faixa.fim()).isEqualTo(TOTAL - 1);
    }

    @ParameterizedTest(name = "Range {0} é insatisfazível")
    @CsvSource({"bytes=1000-", "bytes=1000-1100", "bytes=5000-6000"})
    @DisplayName("offset a partir do tamanho total é insatisfazível (→ 416)")
    void faixaInsatisfazivel(String range) {
        assertThat(resolver(range)).isNull();
    }

    @Test
    @DisplayName("fim além do total é truncado, não recusado")
    void fimAlemDoTotalTrunca() {
        ApkStreamService.Faixa faixa = resolver("bytes=900-99999");

        assertThat(faixa.inicio()).isEqualTo(900);
        assertThat(faixa.fim()).isEqualTo(TOTAL - 1);
        assertThat(faixa.tamanho()).isEqualTo(100);
    }

    @Test
    @DisplayName("Range malformado é ignorado e serve tudo")
    void rangeMalformado() {
        ApkStreamService.Faixa faixa = resolver("bytes=abc");

        assertThat(faixa.parcial()).isFalse();
        assertThat(faixa.tamanho()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("múltiplas faixas caem para resposta completa em vez de multipart")
    void multiplasFaixas() {
        ApkStreamService.Faixa faixa = resolver("bytes=0-99,200-299");

        assertThat(faixa.parcial()).isFalse();
        assertThat(faixa.tamanho()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("If-Range igual ao ETag mantém a faixa — retomada válida")
    void ifRangeQueBate() {
        ApkStreamService.Faixa faixa = service.resolverFaixa("bytes=400-499", ETAG, ETAG, TOTAL);

        assertThat(faixa.parcial()).isTrue();
        assertThat(faixa.inicio()).isEqualTo(400);
    }

    @Test
    @DisplayName("If-Range divergente devolve o arquivo inteiro (FR-011)")
    void ifRangeDivergenteServeTudo() {
        ApkStreamService.Faixa faixa = service.resolverFaixa("bytes=400-499", "\"outra-versao\"",
                ETAG, TOTAL);

        assertThat(faixa.parcial()).isFalse();
        assertThat(faixa.inicio()).isZero();
        assertThat(faixa.tamanho()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("arquivo de tamanho zero não mente no Content-Length")
    void arquivoVazio() {
        ApkStreamService.Faixa faixa = service.resolverFaixa(null, null, ETAG, 0);

        assertThat(faixa.tamanho()).isZero();
    }
}
