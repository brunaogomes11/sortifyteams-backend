package com.gomesdev.sortifyteams.domain.versaoapp.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Metadados informados pelo admin ao publicar uma versão de runtime
 * (spec 002, Fluxo 7). O binário vem à parte, como multipart.
 */
public record PublicarVersaoRequest(

        @NotBlank(message = "informe a versão (ex.: 1.1.0)")
        String versao,

        @NotNull(message = "informe o versionCode")
        @Min(value = 1, message = "versionCode deve ser maior que zero")
        Integer versionCode,

        @NotBlank(message = "informe o runtimeVersion")
        String runtimeVersion,

        @NotNull(message = "informe o versionCode mínimo suportado")
        @Min(value = 1, message = "versionCode mínimo deve ser maior que zero")
        Integer versionCodeMinimo,

        String notas
) {
}
