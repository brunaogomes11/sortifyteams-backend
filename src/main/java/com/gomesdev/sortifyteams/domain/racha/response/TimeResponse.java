package com.gomesdev.sortifyteams.domain.racha.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Time gerado pelo sorteio")
public record TimeResponse(
        @Schema(description = "Número do time") int numero,
        @Schema(description = "Jogadores do time") List<ParticipanteResponse> jogadores
) {
}
