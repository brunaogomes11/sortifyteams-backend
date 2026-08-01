package com.gomesdev.sortifyteams.domain.versaoapp;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Lê {@code versionCode}, {@code versionName} e o {@code runtimeVersion} do
 * Expo diretamente do {@code AndroidManifest.xml} binário dentro do APK
 * (spec 002, FR-017 revisado).
 *
 * <p>{@code versionCode}/{@code versionName} vêm de {@link ApkMeta}, que a
 * biblioteca já resolve do manifesto. O {@code runtimeVersion} não tem
 * acessor próprio na biblioteca — é uma tag {@code <meta-data>} que o plugin
 * de config do {@code expo-updates} injeta durante o
 * {@code prebuild}/{@code eas build}
 * ({@code expo.modules.updates.EXPO_RUNTIME_VERSION}) — então é extraído
 * lendo o XML já decodificado por {@link ApkFile#getManifestXml()} com um
 * parser DOM comum, em vez de mexer no formato binário AXML na mão.
 */
@Component
public class ApkManifestReader {

    private static final String META_RUNTIME_VERSION = "expo.modules.updates.EXPO_RUNTIME_VERSION";

    public ApkManifest ler(Path arquivoApk) {
        try (ApkFile apk = new ApkFile(arquivoApk.toFile())) {
            ApkMeta meta = apk.getApkMeta();
            if (meta.getVersionCode() == null) {
                throw new IllegalArgumentException(
                        "O APK não declara versionCode no AndroidManifest.xml.");
            }
            String runtimeVersion = lerRuntimeVersion(apk.getManifestXml());
            if (runtimeVersion == null || runtimeVersion.isBlank()) {
                throw new IllegalArgumentException(
                        "Não encontrei o runtimeVersion (expo.modules.updates.EXPO_RUNTIME_VERSION) "
                                + "no AndroidManifest.xml — confirme que o build foi gerado com o plugin "
                                + "expo-updates configurado em app.json.");
            }
            return new ApkManifest(meta.getVersionCode().intValue(), meta.getVersionName(), runtimeVersion);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Não consegui ler o AndroidManifest.xml deste APK — arquivo corrompido ou formato inesperado.", e);
        }
    }

    // Package-private de propósito: é a parte da lógica que não veio pronta de
    // terceiros (a extração do <meta-data> específico do expo-updates) e por
    // isso é testável sem precisar de um APK real — recebe o XML já decodificado
    // pela biblioteca, não o AXML binário.
    String lerRuntimeVersion(String manifestXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Manifesto vem de um APK que o próprio admin enviou — ainda assim,
            // desliga resolução de entidade externa por padrão de segurança.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            // O handler padrão do JAXP imprime erro de parse direto no stderr,
            // mesmo quando o chamador vai tratar a exceção — um APK malformado
            // enviado pelo painel não precisa virar ruído no log do servidor.
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            Document doc = builder.parse(
                    new ByteArrayInputStream(manifestXml.getBytes(StandardCharsets.UTF_8)));

            NodeList metaDatas = doc.getElementsByTagName("meta-data");
            for (int i = 0; i < metaDatas.getLength(); i++) {
                Element el = (Element) metaDatas.item(i);
                if (META_RUNTIME_VERSION.equals(el.getAttribute("android:name"))) {
                    return el.getAttribute("android:value");
                }
            }
            return null;
        } catch (Exception e) {
            throw new IllegalArgumentException("Falha ao interpretar o AndroidManifest.xml do APK.", e);
        }
    }
}
