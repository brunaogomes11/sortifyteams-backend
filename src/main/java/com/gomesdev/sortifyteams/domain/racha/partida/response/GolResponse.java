package com.gomesdev.sortifyteams.domain.racha.partida.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Gol de uma partida do racha ao vivo")
public record GolResponse(
        @Schema(description = "ID ULID do gol") String id,
        @Schema(description = "ID da partida") String partidaId,
        @Schema(description = "Número do time que marcou") int timeNumero,
        @Schema(description = "Participante autor (nulo para gol sem autor)") String participanteId,
        @Schema(description = "Nome do autor (nulo para gol sem autor)") String participanteNome,
        @Schema(description = "Usuário que registrou o gol no app") String registradoPorUsuarioId,
        @Schema(description = "Segundo da partida em que o gol saiu") Integer tempoSeg,
        @Schema(description = "Momento do registro") LocalDateTime criadoEm
) {
}
