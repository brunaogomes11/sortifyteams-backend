package com.gomesdev.sortifyteams.domain.racha.partida.response;

import com.gomesdev.sortifyteams.enums.CriterioEmpateEnum;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Snapshot ao vivo do racha — mesmo payload do GET /ao-vivo e do tópico STOMP /topic/rachas/{id}")
public record RachaAoVivoResponse(
        @Schema(description = "ID ULID do racha") String rachaId,
        @Schema(description = "Status do racha") StatusRachaEnum status,
        @Schema(description = "Hora do servidor no envio (para o app calcular o offset de relógio)") LocalDateTime agora,
        @Schema(description = "Momento em que o racha entrou ao vivo") LocalDateTime iniciadoEm,
        @Schema(description = "Duração padrão sugerida para a próxima partida (segundos)") Integer duracaoPartidaSeg,
        @Schema(description = "Critério de desempate para partidas 0x0") CriterioEmpateEnum criterioEmpateZero,
        @Schema(description = "Critério de desempate para empates com gols") CriterioEmpateEnum criterioEmpateGols,
        @Schema(description = "Partida em andamento (nula se nenhuma)") PartidaResponse partidaAtual,
        @Schema(description = "Partidas já encerradas, em ordem (histórico)") List<PartidaResponse> partidasEncerradas,
        @Schema(description = "Sugestão de próxima partida (nula quando indeterminável ou fora de intervalo)")
        SugestaoPartidaResponse sugestaoProximaPartida
) {
}
