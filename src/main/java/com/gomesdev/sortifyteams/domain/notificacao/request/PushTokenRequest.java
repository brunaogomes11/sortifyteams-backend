package com.gomesdev.sortifyteams.domain.notificacao.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Registro de push token do dispositivo (Expo)")
public record PushTokenRequest(
        @NotBlank @Schema(description = "ExponentPushToken[...]") String expoToken,
        @NotBlank @Pattern(regexp = "IOS|ANDROID") @Schema(description = "Plataforma", allowableValues = {"IOS", "ANDROID"}) String plataforma
) {
}
