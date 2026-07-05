package com.gomesdev.sortifyteams.domain.racha.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dados do convidado ao entrar num racha pelo link de convite (C9)")
public record EntrarConviteRequest(
        @NotNull @Min(1) @Max(5) @Schema(description = "Nível técnico de 1 a 5") Integer nivelTecnico,
        @Schema(description = "Se é goleiro") Boolean eGoleiro
) {
}
