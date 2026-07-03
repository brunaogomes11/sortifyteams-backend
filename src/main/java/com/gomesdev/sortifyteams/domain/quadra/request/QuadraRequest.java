package com.gomesdev.sortifyteams.domain.quadra.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de cadastro/edição de quadra")
public record QuadraRequest(
        @NotBlank @Size(max = 80) @Schema(description = "Nome da quadra") String nome,
        @NotBlank @Size(max = 255) @Schema(description = "Endereço completo") String endereco,
        @NotBlank @Size(max = 60) @Schema(description = "Contato (telefone/WhatsApp)") String contato,
        @Schema(description = "Se a quadra aparece na busca (padrão true)") Boolean ativa
) {
}
