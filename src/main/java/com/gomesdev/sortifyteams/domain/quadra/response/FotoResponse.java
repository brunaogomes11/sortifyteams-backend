package com.gomesdev.sortifyteams.domain.quadra.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Foto de quadra")
public record FotoResponse(
        @Schema(description = "ID ULID da foto") String id,
        @Schema(description = "URL pública") String url,
        @Schema(description = "Ordem de exibição") int ordem
) {
}
