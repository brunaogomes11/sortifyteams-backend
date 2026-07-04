package com.gomesdev.sortifyteams.domain.quadra.response;

import com.gomesdev.sortifyteams.domain.quadra.Quadra;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Visão pública da quadra para organizadores (FR-016: sem dados do dono). */
@Schema(description = "Quadra disponível para reserva (visão do organizador)")
public record QuadraPublicaResponse(
        @Schema(description = "ID ULID") String id,
        @Schema(description = "Nome") String nome,
        @Schema(description = "Endereço") String endereco,
        @Schema(description = "Contato para combinar pagamento (C7)") String contato,
        @Schema(description = "URLs das fotos") List<String> fotos,
        @Schema(description = "Grade semanal") List<HorarioResponse> horarios
) {
    public QuadraPublicaResponse(Quadra quadra, List<String> fotos, List<HorarioResponse> horarios) {
        this(quadra.getId(), quadra.getNome(), quadra.getEndereco(), quadra.getContato(), fotos, horarios);
    }
}
