package com.gomesdev.sortifyteams.domain.quadra.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

@Schema(description = "Faixa de horários — dividida em reservas de 1 hora")
public record HorarioRequest(
        @NotNull @Min(0) @Max(6) @Schema(description = "0=domingo ... 6=sábado") Integer diaSemana,
        @NotNull @Schema(description = "Hora de início da faixa") LocalTime horaInicio,
        @NotNull @Schema(description = "Hora de fim da faixa") LocalTime horaFim,
        @NotNull @DecimalMin("0.00") @Schema(description = "Preço por hora") BigDecimal preco
) {
}
