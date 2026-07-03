package com.gomesdev.sortifyteams.domain.racha.response;

import com.gomesdev.sortifyteams.domain.racha.Racha;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Item da lista Meus Rachas")
public record RachaResumoResponse(
        @Schema(description = "ID ULID do racha") String id,
        @Schema(description = "Nome do esporte") String esporteNome,
        @Schema(description = "Slug do ícone do esporte") String esporteIcone,
        @Schema(description = "Data do jogo") LocalDate data,
        @Schema(description = "Horário do jogo") LocalTime horario,
        @Schema(description = "Status") StatusRachaEnum status,
        @Schema(description = "Total de participantes") long qtdParticipantes,
        @Schema(description = "Se o usuário logado é o organizador") boolean organizador
) {
    public RachaResumoResponse(Racha racha, String esporteNome, String esporteIcone,
                               long qtdParticipantes, boolean organizador) {
        this(racha.getId(), esporteNome, esporteIcone, racha.getData(), racha.getHorario(),
                racha.getStatus(), qtdParticipantes, organizador);
    }
}
