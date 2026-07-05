package com.gomesdev.sortifyteams.domain.usuario.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Perfil do jogador (Fluxo 6)")
public record PerfilResponse(
        @Schema(description = "ID ULID") String id,
        @Schema(description = "Nome completo") String nomeCompleto,
        @Schema(description = "Nome de usuário") String username,
        @Schema(description = "E-mail") String email,
        @Schema(description = "Contato") String contato,
        @Schema(description = "URL da foto de perfil") String fotoUrl,
        @Schema(description = "Rachas concluídos que participou/organizou") long rachasParticipados,
        @Schema(description = "ID do esporte preferido (nulo se sem histórico)") String esportePreferidoId,
        @Schema(description = "Nome do esporte preferido") String esportePreferidoNome,
        @Schema(description = "Se o esporte preferido foi definido manualmente (C11)") boolean esportePreferidoManual
) {
}
