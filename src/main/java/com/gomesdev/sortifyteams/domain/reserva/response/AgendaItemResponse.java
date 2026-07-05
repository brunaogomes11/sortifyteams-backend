package com.gomesdev.sortifyteams.domain.reserva.response;

import com.gomesdev.sortifyteams.domain.quadra.response.HorarioResponse;
import com.gomesdev.sortifyteams.enums.StatusReservaEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Item da Agenda do dono (T034). Expõe só nome e contato do organizador (FR-016). */
@Schema(description = "Reserva na agenda do dono de quadra")
public record AgendaItemResponse(
        @Schema(description = "ID da reserva") String reservaId,
        @Schema(description = "ID da quadra") String quadraId,
        @Schema(description = "Nome da quadra") String quadraNome,
        @Schema(description = "Data do jogo") LocalDate data,
        @Schema(description = "Status") StatusReservaEnum status,
        @Schema(description = "Valor total") BigDecimal precoTotal,
        @Schema(description = "Slots reservados") List<HorarioResponse> horarios,
        @Schema(description = "Nome de quem reservou") String organizadorNome,
        @Schema(description = "Contato de quem reservou (para combinar pagamento — C7)") String organizadorContato
) {
}
