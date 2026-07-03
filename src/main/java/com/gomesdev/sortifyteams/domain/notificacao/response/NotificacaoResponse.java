package com.gomesdev.sortifyteams.domain.notificacao.response;

import com.gomesdev.sortifyteams.domain.notificacao.Notificacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Notificação in-app")
public record NotificacaoResponse(
        @Schema(description = "ID ULID") String id,
        @Schema(description = "Tipo") String tipo,
        @Schema(description = "Título") String titulo,
        @Schema(description = "Corpo") String corpo,
        @Schema(description = "Se já foi lida") boolean lida,
        @Schema(description = "Criada em") LocalDateTime criadaEm
) {
    public NotificacaoResponse(Notificacao n) {
        this(n.getId(), n.getTipo(), n.getTitulo(), n.getCorpo(), n.isLida(), n.getCriadaEm());
    }
}
