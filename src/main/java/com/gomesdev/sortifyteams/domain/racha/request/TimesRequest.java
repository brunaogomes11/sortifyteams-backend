package com.gomesdev.sortifyteams.domain.racha.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Ajuste manual dos times sorteados (tela "Editar times"): o organizador troca
 * jogadores de time depois do sorteio. Envia só os participantes que mudaram —
 * cada um com o número do time de destino. É uma reatribuição parcial: quem não
 * vier na lista mantém o time atual.
 */
@Schema(description = "Reatribuição manual de jogadores aos times do sorteio")
public record TimesRequest(
        @NotEmpty
        @Schema(description = "Participantes movidos e seus times de destino")
        List<@Valid Atribuicao> atribuicoes
) {
    @Schema(description = "Participante e o número do time para onde ele vai")
    public record Atribuicao(
            @NotBlank @Schema(description = "ID do participante") String participanteId,
            @NotNull @Min(1) @Schema(description = "Número do time de destino") Integer timeNumero
    ) {
    }
}
