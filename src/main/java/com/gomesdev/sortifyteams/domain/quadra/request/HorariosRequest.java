package com.gomesdev.sortifyteams.domain.quadra.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Grade semanal completa da quadra (substitui a anterior)")
public record HorariosRequest(
        @NotNull @Valid @Schema(description = "Slots da grade") List<HorarioRequest> horarios
) {
}
