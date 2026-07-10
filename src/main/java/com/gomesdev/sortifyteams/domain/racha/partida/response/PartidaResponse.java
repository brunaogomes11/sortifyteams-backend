package com.gomesdev.sortifyteams.domain.racha.partida.response;

import com.gomesdev.sortifyteams.enums.StatusPartidaEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Partida do racha ao vivo, com placar derivado dos gols")
public record PartidaResponse(
        @Schema(description = "ID ULID da partida") String id,
        @Schema(description = "Ordem da partida no racha (1-based)") int ordem,
        @Schema(description = "Número do time do lado A") int timeNumeroA,
        @Schema(description = "Número do time do lado B") int timeNumeroB,
        @Schema(description = "Status da partida") StatusPartidaEnum status,
        @Schema(description = "Duração prevista em segundos") Integer duracaoPrevistaSeg,
        @Schema(description = "Início da partida") LocalDateTime iniciadaEm,
        @Schema(description = "Encerramento da partida") LocalDateTime encerradaEm,
        @Schema(description = "Gols do lado A") int placarA,
        @Schema(description = "Gols do lado B") int placarB,
        @Schema(description = "Time vencedor (placar ou critério de empate); nulo = em andamento ou empate sem resolução")
        Integer vencedorTimeNumero,
        @Schema(description = "Gols em ordem cronológica") List<GolResponse> gols
) {
}
