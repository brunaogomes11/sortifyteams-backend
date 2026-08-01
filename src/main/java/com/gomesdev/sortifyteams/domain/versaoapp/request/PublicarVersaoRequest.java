package com.gomesdev.sortifyteams.domain.versaoapp.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * O que o admin efetivamente decide ao publicar uma versão de runtime
 * (spec 002, Fluxo 7). {@code versao}, {@code versionCode} e
 * {@code runtimeVersion} <b>não</b> entram aqui — vêm do próprio
 * {@code AndroidManifest.xml} do APK enviado ({@link com.gomesdev.sortifyteams.domain.versaoapp.ApkManifestReader}),
 * porque já estão lá e digitar de novo só cria chance de divergir do que o
 * build realmente contém.
 */
public record PublicarVersaoRequest(

        @NotNull(message = "informe o versionCode mínimo suportado")
        @Min(value = 1, message = "versionCode mínimo deve ser maior que zero")
        Integer versionCodeMinimo,

        String notas
) {
}
