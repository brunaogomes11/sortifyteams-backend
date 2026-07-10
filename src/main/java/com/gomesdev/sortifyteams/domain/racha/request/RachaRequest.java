package com.gomesdev.sortifyteams.domain.racha.request;

import com.gomesdev.sortifyteams.enums.CriterioEmpateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Dados de criação de um racha")
public record RachaRequest(
        @NotBlank @Schema(description = "ID ULID do esporte") String esporteId,
        @FutureOrPresent @Schema(description = "Data do jogo (opcional, não pode ser no passado)") LocalDate data,
        @Schema(description = "Horário do jogo (opcional)") LocalTime horario,
        @Size(max = 140) @Schema(description = "Local do jogo em texto livre (opcional)") String local,
        @Min(2) @Max(20) @Schema(description = "Quantidade de times (padrão 2, máximo 20)") Integer qtdTimes,
        @Schema(description = "Usar nível técnico dos jogadores (estrelas e balanceamento). Padrão: true") Boolean usaNivelTecnico,
        @Schema(description = "Balancear times por nível técnico (C5)") Boolean balancearNivel,
        @Min(1) @Schema(description = "Limite opcional de participantes (C9)") Integer limiteVagas,
        @Schema(description = "Racha público — aparece na busca de rachas próximos") Boolean publico,
        @Schema(description = "Se o organizador também vai jogar (entra como participante)") Boolean organizadorJoga,
        @Min(1) @Max(5) @Schema(description = "Nível técnico do organizador (obrigatório se organizadorJoga=true)")
        Integer organizadorNivelTecnico,
        @Schema(description = "Se o organizador é goleiro (esportes que exigem)") Boolean organizadorGoleiro,
        @Schema(description = "Critério de desempate para partidas 0x0 (padrão: TIME_QUE_FICA)")
        CriterioEmpateEnum criterioEmpateZero,
        @Schema(description = "Critério de desempate para empates com gols (padrão: TIME_QUE_ENTRA)")
        CriterioEmpateEnum criterioEmpateGols
) {
}
