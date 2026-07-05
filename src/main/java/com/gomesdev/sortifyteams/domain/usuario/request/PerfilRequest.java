package com.gomesdev.sortifyteams.domain.usuario.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Atualização do perfil do usuário (Fluxo 6)")
public record PerfilRequest(
        @NotBlank @Size(max = 120) @Schema(description = "Nome completo") String nomeCompleto,
        @Size(max = 40) @Schema(description = "Telefone/contato") String contato,
        @Schema(description = "Override manual do esporte preferido (nulo = calculado do histórico — C11)") String esportePreferidoId
) {
}
