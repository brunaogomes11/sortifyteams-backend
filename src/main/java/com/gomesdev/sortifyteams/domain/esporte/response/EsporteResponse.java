package com.gomesdev.sortifyteams.domain.esporte.response;

import com.gomesdev.sortifyteams.domain.esporte.Esporte;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Esporte disponível para rachas")
public record EsporteResponse(
        @Schema(description = "ID ULID") String id,
        @Schema(description = "Nome") String nome,
        @Schema(description = "Slug do ícone") String icone,
        @Schema(description = "Exige goleiro por time") boolean exigeGoleiro,
        @Schema(description = "Mínimo de jogadores por time") int jogadoresMinimosPorTime
) {
    public EsporteResponse(Esporte esporte) {
        this(esporte.getId(), esporte.getNome(), esporte.getIcone(),
                esporte.isExigeGoleiro(), esporte.getJogadoresMinimosPorTime());
    }
}
