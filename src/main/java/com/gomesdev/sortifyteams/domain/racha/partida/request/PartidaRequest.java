package com.gomesdev.sortifyteams.domain.racha.partida.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Criação de uma partida do racha ao vivo (dois times em quadra)")
public record PartidaRequest(
        @NotNull @Min(1) @Schema(description = "Número do time do lado A") Integer timeNumeroA,
        @NotNull @Min(1) @Schema(description = "Número do time do lado B") Integer timeNumeroB,
        @Min(1) @Schema(description = "Duração prevista em segundos (nulo herda a última usada no racha)")
        Integer duracaoPrevistaSeg
) {
}
