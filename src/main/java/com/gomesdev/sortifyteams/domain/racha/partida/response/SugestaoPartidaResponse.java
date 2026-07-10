package com.gomesdev.sortifyteams.domain.racha.partida.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sugestão de próxima partida (dinâmica vencedor-fica + critério de empate + fila)")
public record SugestaoPartidaResponse(
        @Schema(description = "Lado A sugerido (quem fica em quadra)") Integer timeNumeroA,
        @Schema(description = "Lado B sugerido (quem entra)") Integer timeNumeroB
) {
}
