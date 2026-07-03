package com.gomesdev.sortifyteams.domain.quadra.response;

import com.gomesdev.sortifyteams.domain.quadra.Quadra;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Quadra com fotos e grade semanal")
public record QuadraResponse(
        @Schema(description = "ID ULID") String id,
        @Schema(description = "Nome") String nome,
        @Schema(description = "Endereço") String endereco,
        @Schema(description = "Contato") String contato,
        @Schema(description = "Se aparece na busca") boolean ativa,
        @Schema(description = "URLs das fotos") List<FotoResponse> fotos,
        @Schema(description = "Grade semanal") List<HorarioResponse> horarios
) {
    public QuadraResponse(Quadra quadra, List<FotoResponse> fotos, List<HorarioResponse> horarios) {
        this(quadra.getId(), quadra.getNome(), quadra.getEndereco(), quadra.getContato(),
                quadra.isAtiva(), fotos, horarios);
    }
}
