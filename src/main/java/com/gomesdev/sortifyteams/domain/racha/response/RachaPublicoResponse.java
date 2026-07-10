package com.gomesdev.sortifyteams.domain.racha.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

/** Item da lista de rachas públicos próximos (home do jogador). */
@Schema(description = "Racha público visível na busca por proximidade/cidade")
public record RachaPublicoResponse(
        @Schema(description = "ID ULID do racha") String id,
        @Schema(description = "Nome do esporte") String esporteNome,
        @Schema(description = "Slug do ícone do esporte") String esporteIcone,
        @Schema(description = "Data do jogo") LocalDate data,
        @Schema(description = "Horário do jogo") LocalTime horario,
        @Schema(description = "Local do jogo (texto livre)") String local,
        @Schema(description = "Cidade resolvida") String cidade,
        @Schema(description = "Total de participantes") long qtdParticipantes,
        @Schema(description = "Limite de vagas (nulo = sem limite)") Integer limiteVagas,
        @Schema(description = "Primeiro nome do organizador") String organizador,
        @Schema(description = "Distância em km (nulo quando filtrado por cidade)") Double distanciaKm,
        @Schema(description = "Se o usuário logado já é membro deste racha") boolean souMembro,
        @Schema(description = "Token do convite (para entrar no racha público)") String tokenConvite
) {
}
