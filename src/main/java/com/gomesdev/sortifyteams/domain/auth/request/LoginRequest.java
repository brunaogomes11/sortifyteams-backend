package com.gomesdev.sortifyteams.domain.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais de login")
public record LoginRequest(
        @NotBlank @Schema(description = "Nome de usuário") String username,
        @NotBlank @Schema(description = "Senha") String senha
) {
}
