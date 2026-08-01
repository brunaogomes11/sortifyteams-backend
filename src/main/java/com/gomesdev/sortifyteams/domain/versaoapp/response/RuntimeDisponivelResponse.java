package com.gomesdev.sortifyteams.domain.versaoapp.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Versão de APK disponível para download (spec 002, FR-001). Contrato público:
 * nenhum dado de usuário aqui.
 */
@Schema(description = "Versão de runtime (APK) publicada")
public record RuntimeDisponivelResponse(
        String versao,
        int versionCode,
        @Schema(description = "URL de download; aceita Range para retomada")
        String url,
        long tamanhoBytes,
        @Schema(description = "ETag do download — usado no If-Range da retomada")
        String sha256,
        @Schema(description = "Conferido no dispositivo após baixar (o expo-file-system calcula MD5 nativamente)")
        String md5,
        String notas,
        LocalDateTime publicadaEm
) {
}
