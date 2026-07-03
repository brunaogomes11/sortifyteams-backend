package com.gomesdev.sortifyteams.domain.racha.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Jogador a adicionar num racha — informe usuarioId OU nomeAvulso (C3)")
public record ParticipanteRequest(
        @Schema(description = "ID ULID de usuário cadastrado (opcional)") String usuarioId,
        @Size(max = 80) @Schema(description = "Nome avulso digitado (opcional)") String nomeAvulso,
        @NotNull @Min(1) @Max(5) @Schema(description = "Nível técnico de 1 a 5") Integer nivelTecnico,
        @Schema(description = "Se é goleiro") Boolean eGoleiro
) {
}
