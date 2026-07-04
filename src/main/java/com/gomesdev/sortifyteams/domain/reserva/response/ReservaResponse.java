package com.gomesdev.sortifyteams.domain.reserva.response;

import com.gomesdev.sortifyteams.domain.quadra.response.HorarioResponse;
import com.gomesdev.sortifyteams.domain.reserva.Reserva;
import com.gomesdev.sortifyteams.enums.StatusReservaEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Reserva confirmada. Pagamento combinado fora do app com o contato da quadra (C7).")
public record ReservaResponse(
        @Schema(description = "ID ULID da reserva") String id,
        @Schema(description = "ID da quadra") String quadraId,
        @Schema(description = "Nome da quadra") String quadraNome,
        @Schema(description = "Contato da quadra para combinar o pagamento") String quadraContato,
        @Schema(description = "ID do racha") String rachaId,
        @Schema(description = "Data do jogo") LocalDate data,
        @Schema(description = "Status") StatusReservaEnum status,
        @Schema(description = "Preço total (soma dos slots)") BigDecimal precoTotal,
        @Schema(description = "Slots reservados") List<HorarioResponse> horarios
) {
    public ReservaResponse(Reserva reserva, String quadraNome, String quadraContato,
                           List<HorarioResponse> horarios) {
        this(reserva.getId(), reserva.getQuadraId(), quadraNome, quadraContato,
                reserva.getRachaId(), reserva.getData(), reserva.getStatus(),
                reserva.getPrecoTotal(), horarios);
    }
}
