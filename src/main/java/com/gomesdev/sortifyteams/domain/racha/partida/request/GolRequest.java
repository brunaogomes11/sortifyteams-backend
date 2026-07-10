package com.gomesdev.sortifyteams.domain.racha.partida.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Registro de gol (+1 no time) — o autor é opcional")
public record GolRequest(
        @NotNull @Min(1) @Schema(description = "Número do time que marcou") Integer timeNumero,
        @Schema(description = "Participante autor do gol (opcional)") String participanteId
) {
}
