package com.gomesdev.sortifyteams.domain.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais para reenviar a solicitação de acesso de dono de quadra rejeitado (C13)")
public record ReenviarSolicitacaoRequest(
        @NotBlank @Schema(description = "Nome de usuário") String username,
        @NotBlank @Schema(description = "Senha") String senha
) {
}
