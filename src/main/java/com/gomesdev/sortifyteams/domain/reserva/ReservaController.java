package com.gomesdev.sortifyteams.domain.reserva;

import com.gomesdev.sortifyteams.config.ErrorDetails;
import com.gomesdev.sortifyteams.domain.reserva.request.ReservaRequest;
import com.gomesdev.sortifyteams.domain.reserva.response.ReservaResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservas")
@Tag(name = "Reservas", description = "Reserva de quadras para rachas (Fluxo 4). Pagamento fora do app (C7).")
public class ReservaController {

    private final ReservaService service;

    public ReservaController(ReservaService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Reserva um ou mais horários de uma quadra para um racha (FR-008/009)",
            description = "O primeiro que confirma leva; conflito responde 409 com os horários ainda livres na data (C8).")
    @ApiResponse(responseCode = "201", description = "Reserva confirmada",
            content = @Content(schema = @Schema(implementation = ReservaResponse.class)))
    @ApiResponse(responseCode = "409", description = "Horário já reservado — mensagem traz alternativas livres",
            content = @Content(schema = @Schema(implementation = ErrorDetails.ErrorResponse.class)))
    public ResponseEntity<ReservaResponse> criar(@Valid @RequestBody ReservaRequest request,
                                                 @AuthenticationPrincipal Usuario usuario) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request, usuario));
        } catch (DataIntegrityViolationException e) {
            // Corrida entre transações (C8): a transação da criação já foi
            // revertida; monta o 409 com alternativas numa transação nova.
            throw service.conflito(request.quadraId(), request.data(), true);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha uma reserva (organizador do racha ou dono da quadra)")
    public ResponseEntity<ReservaResponse> detalhar(@PathVariable String id,
                                                    @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.detalhar(id, usuario));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancela a reserva (organizador) — libera os horários e notifica o dono (C10)")
    public ResponseEntity<Void> cancelar(@PathVariable String id,
                                         @AuthenticationPrincipal Usuario usuario) {
        service.cancelar(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
