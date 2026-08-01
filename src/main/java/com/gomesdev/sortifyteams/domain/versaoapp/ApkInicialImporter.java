package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Importa o APK distribuído antes da spec 002 como primeira versão publicada
 * (T006), para que a migração não deixe os links já espalhados sem destino.
 *
 * <p>Idempotente por construção: só roda quando <b>não existe nenhuma versão
 * publicada</b>. Depois da primeira execução, publicar passa a ser
 * exclusivamente pelo painel.
 *
 * <p>A origem é configurável ({@code app.versao.apk-inicial}) e aceita tanto
 * {@code classpath:} quanto {@code file:} — o padrão aponta para o arquivo
 * legado dentro do JAR, que sai do repositório assim que este import rodar em
 * produção (C24).
 *
 * <p>Usa {@link VersaoAppService#publicarComMetadadosExplicitos} em vez do
 * caminho normal de publicação: este APK antecede o {@code expo-updates}, não
 * tem {@code runtimeVersion} embutido no manifesto para o
 * {@link ApkManifestReader} extrair — os valores vêm de configuração porque,
 * neste caso específico, não têm de onde mais vir.
 */
@Component
public class ApkInicialImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApkInicialImporter.class);

    private final VersaoAppService service;
    private final VersaoRuntimeRepository repository;
    private final String origem;
    private final String versao;
    private final int versionCode;
    private final String runtimeVersion;

    public ApkInicialImporter(VersaoAppService service,
                              VersaoRuntimeRepository repository,
                              @Value("${app.versao.apk-inicial:classpath:static/downloads/sortify-teams-v1.0.0.apk}")
                              String origem,
                              @Value("${app.versao.apk-inicial-versao:1.0.0}") String versao,
                              @Value("${app.versao.apk-inicial-version-code:1}") int versionCode,
                              @Value("${app.versao.apk-inicial-runtime:1}") String runtimeVersion) {
        this.service = service;
        this.repository = repository;
        this.origem = origem;
        this.versao = versao;
        this.versionCode = versionCode;
        this.runtimeVersion = runtimeVersion;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (origem == null || origem.isBlank()) {
            return;
        }
        if (repository.count() > 0) {
            return; // já há versão publicada — nada a importar
        }
        Resource recurso = new DefaultResourceLoader().getResource(origem);
        if (!recurso.exists()) {
            log.info("Nenhum APK inicial em {} — publique a primeira versão pelo painel.", origem);
            return;
        }
        try {
            VersaoRuntime importada = service.publicarComMetadadosExplicitos(
                    FonteApk.de(recurso, "zerinho-%s.apk".formatted(versao)),
                    versao, versionCode, runtimeVersion, 1,
                    "Versão importada da distribuição anterior à central de atualizações.",
                    PlataformaAppEnum.ANDROID, null);
            log.info("APK inicial importado: versão {} (versionCode {}), {} bytes.",
                    importada.getVersao(), importada.getVersionCode(), importada.getTamanhoBytes());
        } catch (RuntimeException e) {
            // Import é conveniência de migração: falhar aqui não pode derrubar o
            // backend — o admin ainda pode publicar pelo painel.
            log.error("Falha ao importar o APK inicial de {}: {}", origem, e.getMessage());
        }
    }
}
