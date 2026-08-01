package com.gomesdev.sortifyteams.domain.versaoapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Extração do {@code runtimeVersion} a partir do XML já decodificado do
 * manifesto (spec 002 — FR-017 revisado). A decodificação do AXML binário em
 * si é responsabilidade da biblioteca (net.dongliu:apk-parser).
 *
 * <p>{@link #leEndToEndDeUmApkReal()} usa um APK mínimo de verdade
 * ({@code src/test/resources/fixtures/apk-teste-fixture.apk}, ~1 KB, gerado
 * com {@code aapt2 link} a partir de um AndroidManifest.xml de teste) — prova
 * o caminho inteiro (zip → AXML binário → ApkMeta/manifestXml) sem depender
 * de um APK de produção de dezenas de MB. Os demais testes cobrem só a lógica
 * própria (achar o {@code <meta-data>} certo), com XML escrito à mão.
 */
class ApkManifestReaderTest {

    private final ApkManifestReader reader = new ApkManifestReader();

    @Test
    @DisplayName("lê versionCode, versionName e runtimeVersion de um APK real, ponta a ponta")
    void leEndToEndDeUmApkReal() throws Exception {
        Path temp = copiarFixtureParaTemp();
        try {
            ApkManifest manifest = reader.ler(temp);

            assertThat(manifest.versionCode()).isEqualTo(7);
            assertThat(manifest.versionName()).isEqualTo("9.9.9");
            assertThat(manifest.runtimeVersion()).isEqualTo("42");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path copiarFixtureParaTemp() throws Exception {
        Path temp = Files.createTempFile("fixture-", ".apk");
        try (InputStream in = getClass().getResourceAsStream("/fixtures/apk-teste-fixture.apk")) {
            if (in == null) {
                throw new IllegalStateException("Fixture não encontrada no classpath de teste.");
            }
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
        return temp;
    }

    private String manifestCom(String... metaDatas) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          android:versionCode="2" android:versionName="1.1.0">
                    <application>
                """);
        for (String meta : metaDatas) {
            sb.append(meta).append('\n');
        }
        sb.append("    </application>\n</manifest>");
        return sb.toString();
    }

    @Test
    @DisplayName("acha o runtimeVersion entre outras meta-data do expo-updates")
    void achaRuntimeVersionEntreOutrasMetaDatas() {
        String xml = manifestCom(
                "<meta-data android:name=\"expo.modules.updates.ENABLED\" android:value=\"true\" />",
                "<meta-data android:name=\"expo.modules.updates.EXPO_RUNTIME_VERSION\" android:value=\"1\" />",
                "<meta-data android:name=\"expo.modules.updates.EXPO_UPDATE_URL\" android:value=\"https://x\" />");

        assertThat(reader.lerRuntimeVersion(xml)).isEqualTo("1");
    }

    @Test
    @DisplayName("runtimeVersion com múltiplos segmentos (ex.: apos upgrade nativo)")
    void runtimeVersionComValorNaoTrivial() {
        String xml = manifestCom(
                "<meta-data android:name=\"expo.modules.updates.EXPO_RUNTIME_VERSION\" android:value=\"3\" />");

        assertThat(reader.lerRuntimeVersion(xml)).isEqualTo("3");
    }

    @Test
    @DisplayName("manifesto sem expo-updates configurado devolve null (APK legado)")
    void semExpoUpdatesDevolveNull() {
        String xml = manifestCom(
                "<meta-data android:name=\"com.google.android.geo.API_KEY\" android:value=\"abc\" />");

        assertThat(reader.lerRuntimeVersion(xml)).isNull();
    }

    @Test
    @DisplayName("manifesto sem nenhuma meta-data devolve null")
    void semMetaDataNenhuma() {
        assertThat(reader.lerRuntimeVersion(manifestCom())).isNull();
    }

    @Test
    @DisplayName("XML malformado lança erro claro, não exceção genérica")
    void xmlMalformadoLancaErroClaro() {
        assertThatThrownBy(() -> reader.lerRuntimeVersion("<manifest><application>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AndroidManifest.xml");
    }

    @Test
    @DisplayName("não confunde meta-data de nome parecido com o exato do runtimeVersion")
    void naoConfundeNomeParecido() {
        String xml = manifestCom(
                "<meta-data android:name=\"expo.modules.updates.EXPO_RUNTIME_VERSION_X\" android:value=\"9\" />");

        assertThat(reader.lerRuntimeVersion(xml)).isNull();
    }
}
