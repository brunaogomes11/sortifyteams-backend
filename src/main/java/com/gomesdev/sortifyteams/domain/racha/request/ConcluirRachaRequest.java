package com.gomesdev.sortifyteams.domain.racha.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Dados de conclusão de um racha")
public record ConcluirRachaRequest(
        @Min(1) @Schema(description = "Duração da partida em segundos, registrada pelo cronômetro (C1)") Integer duracaoPartidaSeg
) {
}
