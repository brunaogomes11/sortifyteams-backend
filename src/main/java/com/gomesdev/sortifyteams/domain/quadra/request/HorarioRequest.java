package com.gomesdev.sortifyteams.domain.quadra.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

@Schema(description = "Slot da grade semanal recorrente")
public record HorarioRequest(
        @NotNull @Min(0) @Max(6) @Schema(description = "0=domingo ... 6=sábado") Integer diaSemana,
        @NotNull @Schema(description = "Hora de início") LocalTime horaInicio,
        @NotNull @Schema(description = "Hora de fim") LocalTime horaFim,
        @NotNull @DecimalMin("0.00") @Schema(description = "Preço do slot") BigDecimal preco
) {
}
