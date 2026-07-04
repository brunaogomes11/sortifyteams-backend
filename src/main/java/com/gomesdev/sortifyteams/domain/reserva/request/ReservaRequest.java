package com.gomesdev.sortifyteams.domain.reserva.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Pedido de reserva de um ou mais horários de uma quadra para um racha (Fluxo 4)")
public record ReservaRequest(
        @NotBlank @Schema(description = "ID ULID da quadra") String quadraId,
        @NotBlank @Schema(description = "ID ULID do racha do organizador") String rachaId,
        @NotNull @FutureOrPresent @Schema(description = "Data do jogo") LocalDate data,
        @NotEmpty @Schema(description = "IDs dos slots da grade semanal (tb_quadra_horario)") List<String> quadraHorarioIds
) {
}
