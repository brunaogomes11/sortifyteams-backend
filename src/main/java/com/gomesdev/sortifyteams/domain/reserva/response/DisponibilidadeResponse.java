package com.gomesdev.sortifyteams.domain.reserva.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Disponibilidade dos horários de uma quadra numa data")
public record DisponibilidadeResponse(
        @Schema(description = "Data consultada") LocalDate data,
        @Schema(description = "Slots do dia da semana correspondente") List<Slot> slots
) {
    @Schema(description = "Slot da grade com situação na data")
    public record Slot(
            @Schema(description = "ID do slot (tb_quadra_horario)") String id,
            @Schema(description = "Hora de início") LocalTime horaInicio,
            @Schema(description = "Hora de fim") LocalTime horaFim,
            @Schema(description = "Preço") BigDecimal preco,
            @Schema(description = "Se está livre nesta data") boolean disponivel
    ) {
    }
}
