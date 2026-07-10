package com.gomesdev.sortifyteams.domain.racha.request;

import com.gomesdev.sortifyteams.enums.CriterioEmpateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Edição de um racha já criado e aberto (tela "Editar racha").
 * Todos os campos são opcionais: um valor nulo mantém o que já está salvo
 * (atualização parcial).
 */
@Schema(description = "Edição de um racha aberto (nível técnico, público, local, etc.)")
public record RachaConfigRequest(
        @Size(max = 140) @Schema(description = "Local/nome do racha (texto livre)") String local,
        @Schema(description = "Se o racha usa nível técnico (estrelas e balanceamento)") Boolean usaNivelTecnico,
        @Schema(description = "Se o sorteio balanceia os times por nível técnico") Boolean balancearNivel,
        @Schema(description = "Se o racha é público (busca de rachas próximos)") Boolean publico,
        @Min(1) @Schema(description = "Limite de vagas (nulo mantém o atual)") Integer limiteVagas,
        @Min(2) @Max(20) @Schema(description = "Quantidade de times do sorteio") Integer qtdTimes,
        @Schema(description = "Se os goleiros entram no sorteio (um por time). Desligado: grupo à parte.")
        Boolean incluirGoleirosNoSorteio,
        @Schema(description = "Critério de desempate para partidas 0x0") CriterioEmpateEnum criterioEmpateZero,
        @Schema(description = "Critério de desempate para empates com gols") CriterioEmpateEnum criterioEmpateGols
) {
}
