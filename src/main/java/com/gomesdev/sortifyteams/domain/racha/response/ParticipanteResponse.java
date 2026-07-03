package com.gomesdev.sortifyteams.domain.racha.response;

import com.gomesdev.sortifyteams.domain.racha.ParticipanteRacha;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Jogador de um racha")
public record ParticipanteResponse(
        @Schema(description = "ID do participante") String id,
        @Schema(description = "ID do usuário cadastrado (nulo para avulso)") String usuarioId,
        @Schema(description = "Nome exibido (nome avulso ou nome do usuário)") String nome,
        @Schema(description = "Nível técnico de 1 a 5") int nivelTecnico,
        @Schema(description = "Se é goleiro") boolean eGoleiro,
        @Schema(description = "Número do time sorteado (nulo antes do sorteio)") Integer timeNumero
) {
    public ParticipanteResponse(ParticipanteRacha participante, String nomeUsuario, Integer timeNumero) {
        this(participante.getId(),
                participante.getUsuarioId(),
                participante.getUsuarioId() != null ? nomeUsuario : participante.getNomeAvulso(),
                participante.getNivelTecnico(),
                participante.isEGoleiro(),
                timeNumero);
    }
}
