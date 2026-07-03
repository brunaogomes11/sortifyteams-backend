package com.gomesdev.sortifyteams.domain.auth.request;

import com.gomesdev.sortifyteams.enums.RoleEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de registro de um novo usuário")
public record RegisterRequest(
        @NotBlank @Size(max = 120)
        @Schema(description = "Nome completo") String nomeCompleto,

        @NotBlank @Size(min = 3, max = 40)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "use apenas letras, números, ponto, hífen e underline")
        @Schema(description = "Nome de usuário para login") String username,

        @NotBlank @Email
        @Schema(description = "E-mail") String email,

        @NotBlank @Size(min = 6, max = 72)
        @Schema(description = "Senha (mínimo 6 caracteres)") String senha,

        @NotNull
        @Schema(description = "Papel escolhido no cadastro", allowableValues = {"JOGADOR", "DONO_QUADRA"}) RoleEnum role
) {
}
