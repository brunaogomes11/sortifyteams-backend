package com.gomesdev.sortifyteams.domain.quadra.response;

import com.gomesdev.sortifyteams.domain.quadra.QuadraHorario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalTime;

@Schema(description = "Slot da grade semanal")
public record HorarioResponse(
        @Schema(description = "ID ULID do slot") String id,
        @Schema(description = "0=domingo ... 6=sábado") int diaSemana,
        @Schema(description = "Hora de início") LocalTime horaInicio,
        @Schema(description = "Hora de fim") LocalTime horaFim,
        @Schema(description = "Preço") BigDecimal preco
) {
    public HorarioResponse(QuadraHorario horario) {
        this(horario.getId(), horario.getDiaSemana(), horario.getHoraInicio(),
                horario.getHoraFim(), horario.getPreco());
    }
}
