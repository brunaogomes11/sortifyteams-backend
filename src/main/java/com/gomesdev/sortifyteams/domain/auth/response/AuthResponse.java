package com.gomesdev.sortifyteams.domain.auth.response;

import com.gomesdev.sortifyteams.domain.usuario.response.UsuarioResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta de autenticação. Para DONO_QUADRA pendente/rejeitado os tokens vêm nulos e o status indica a situação.")
public record AuthResponse(
        @Schema(description = "Access token JWT (nulo se cadastro aguardando aprovação)") String accessToken,
        @Schema(description = "Refresh token opaco (nulo se cadastro aguardando aprovação)") String refreshToken,
        @Schema(description = "Dados do usuário") UsuarioResponse usuario
) {
    public AuthResponse(AuthTokens tokens) {
        this(tokens.accessToken(), tokens.refreshToken(), new UsuarioResponse(tokens.usuario()));
    }
}
