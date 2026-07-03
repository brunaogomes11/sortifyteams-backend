package com.gomesdev.sortifyteams.domain.racha.response;

import com.gomesdev.sortifyteams.domain.esporte.response.EsporteResponse;
import com.gomesdev.sortifyteams.domain.racha.Racha;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Detalhe de um racha")
public record RachaResponse(
        @Schema(description = "ID ULID do racha") String id,
        @Schema(description = "Esporte") EsporteResponse esporte,
        @Schema(description = "ID do organizador") String organizadorId,
        @Schema(description = "Data do jogo") LocalDate data,
        @Schema(description = "Horário do jogo") LocalTime horario,
        @Schema(description = "Status") StatusRachaEnum status,
        @Schema(description = "Quantidade de times") int qtdTimes,
        @Schema(description = "Balancear por nível") boolean balancearNivel,
        @Schema(description = "Limite de vagas (C9)") Integer limiteVagas,
        @Schema(description = "Token do link de convite") String tokenConvite,
        @Schema(description = "Duração registrada da partida (segundos)") Integer duracaoPartidaSeg,
        @Schema(description = "Participantes") List<ParticipanteResponse> participantes,
        @Schema(description = "Times do último sorteio") List<TimeResponse> times
) {
    public RachaResponse(Racha racha, EsporteResponse esporte,
                         List<ParticipanteResponse> participantes, List<TimeResponse> times) {
        this(racha.getId(), esporte, racha.getOrganizadorId(), racha.getData(), racha.getHorario(),
                racha.getStatus(), racha.getQtdTimes(), racha.isBalancearNivel(), racha.getLimiteVagas(),
                racha.getTokenConvite(), racha.getDuracaoPartidaSeg(), participantes, times);
    }
}
