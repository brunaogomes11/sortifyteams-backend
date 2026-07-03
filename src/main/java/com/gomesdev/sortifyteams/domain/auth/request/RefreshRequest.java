package com.gomesdev.sortifyteams.domain.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token para renovação do access token")
public record RefreshRequest(
        @NotBlank @Schema(description = "Refresh token recebido no login/refresh anterior") String refreshToken
) {
}
