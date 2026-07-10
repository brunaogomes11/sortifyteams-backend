package com.gomesdev.sortifyteams.domain.racha.response;

import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

/** Dados mínimos do racha exibidos a quem abre o link de convite (FR-016). */
@Schema(description = "Prévia do racha para o convidado")
public record ConviteResponse(
        @Schema(description = "ID do racha (para navegar após entrar)") String rachaId,
        @Schema(description = "Nome do esporte") String esporteNome,
        @Schema(description = "Ícone do esporte") String esporteIcone,
        @Schema(description = "Se o esporte exige goleiro") boolean exigeGoleiro,
        @Schema(description = "Se o racha usa nível técnico (estrelas ao entrar)") boolean usaNivelTecnico,
        @Schema(description = "Primeiro nome do organizador") String organizador,
        @Schema(description = "Data do jogo") LocalDate data,
        @Schema(description = "Horário do jogo") LocalTime horario,
        @Schema(description = "Local do jogo (texto livre)") String local,
        @Schema(description = "Participantes atuais") long qtdParticipantes,
        @Schema(description = "Limite de vagas (nulo = sem limite)") Integer limiteVagas,
        @Schema(description = "Status do racha") StatusRachaEnum status,
        @Schema(description = "Se quem consulta já participa") boolean jaParticipa
) {
}
