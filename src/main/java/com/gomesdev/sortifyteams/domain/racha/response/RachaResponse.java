package com.gomesdev.sortifyteams.domain.racha.response;

import com.gomesdev.sortifyteams.domain.esporte.response.EsporteResponse;
import com.gomesdev.sortifyteams.domain.racha.Racha;
import com.gomesdev.sortifyteams.enums.CriterioEmpateEnum;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Detalhe de um racha")
public record RachaResponse(
        @Schema(description = "ID ULID do racha") String id,
        @Schema(description = "Esporte") EsporteResponse esporte,
        @Schema(description = "ID do organizador") String organizadorId,
        @Schema(description = "Data do jogo") LocalDate data,
        @Schema(description = "Horário do jogo") LocalTime horario,
        @Schema(description = "Local do jogo (texto livre)") String local,
        @Schema(description = "Se o racha é público (busca de rachas próximos)") boolean publico,
        @Schema(description = "Status") StatusRachaEnum status,
        @Schema(description = "Quantidade de times") int qtdTimes,
        @Schema(description = "Balancear por nível") boolean balancearNivel,
        @Schema(description = "Se o racha usa nível técnico (estrelas na UI)") boolean usaNivelTecnico,
        @Schema(description = "Se os goleiros entram no sorteio (um por time); desligado, viram grupo à parte")
        boolean incluirGoleirosNoSorteio,
        @Schema(description = "Limite de vagas (C9)") Integer limiteVagas,
        @Schema(description = "Token do link de convite") String tokenConvite,
        @Schema(description = "Duração registrada da partida (segundos)") Integer duracaoPartidaSeg,
        @Schema(description = "Momento em que o racha entrou ao vivo (EM_ANDAMENTO)") LocalDateTime iniciadoEm,
        @Schema(description = "Critério de desempate para partidas 0x0") CriterioEmpateEnum criterioEmpateZero,
        @Schema(description = "Critério de desempate para empates com gols (1x1, 2x2...)") CriterioEmpateEnum criterioEmpateGols,
        @Schema(description = "Participantes") List<ParticipanteResponse> participantes,
        @Schema(description = "Times do último sorteio") List<TimeResponse> times,
        @Schema(description = "Nulo quando o racha não é público; caso público, indica se ele tem "
                + "coordenadas resolvidas e por isso aparece na busca por proximidade")
        Boolean localizacaoResolvida
) {
    public RachaResponse(Racha racha, EsporteResponse esporte,
                         List<ParticipanteResponse> participantes, List<TimeResponse> times) {
        this(racha.getId(), esporte, racha.getOrganizadorId(), racha.getData(), racha.getHorario(),
                racha.getLocal(), racha.isPublico(), racha.getStatus(), racha.getQtdTimes(),
                racha.isBalancearNivel(), racha.isUsaNivelTecnico(), racha.isIncluirGoleirosNoSorteio(),
                racha.getLimiteVagas(), racha.getTokenConvite(),
                racha.getDuracaoPartidaSeg(), racha.getIniciadoEm(),
                racha.getCriterioEmpateZero(), racha.getCriterioEmpateGols(),
                participantes, times,
                racha.isPublico() ? racha.getLatitude() != null : null);
    }
}
