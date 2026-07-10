package com.gomesdev.sortifyteams.domain.racha.partida.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "Encerramento de partida — o vencedor só é informado em empates decididos fora do placar (pênaltis)")
public record EncerrarPartidaRequest(
        @Min(1) @Schema(description = "Número do time vencedor do desempate (opcional; ignorado se o placar não está empatado)")
        Integer vencedorTimeNumero
) {
}
