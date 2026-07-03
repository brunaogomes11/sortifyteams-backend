package com.gomesdev.sortifyteams.domain.racha.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Dados de criação de um racha")
public record RachaRequest(
        @NotBlank @Schema(description = "ID ULID do esporte") String esporteId,
        @Schema(description = "Data do jogo (opcional)") LocalDate data,
        @Schema(description = "Horário do jogo (opcional)") LocalTime horario,
        @Min(2) @Schema(description = "Quantidade de times (padrão 2)") Integer qtdTimes,
        @Schema(description = "Balancear times por nível técnico (C5)") Boolean balancearNivel,
        @Min(1) @Schema(description = "Limite opcional de participantes (C9)") Integer limiteVagas
) {
}
