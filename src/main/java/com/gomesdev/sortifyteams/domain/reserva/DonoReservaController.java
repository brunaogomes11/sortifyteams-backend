package com.gomesdev.sortifyteams.domain.reserva;

import com.gomesdev.sortifyteams.domain.reserva.response.AgendaItemResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dono")
@PreAuthorize("hasRole('DONO_QUADRA')")
@Tag(name = "Agenda do Dono", description = "Reservas nas quadras do dono (Fluxo 7)")
public class DonoReservaController {

    private final ReservaService reservaService;

    public DonoReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @GetMapping("/agenda")
    @Operation(summary = "Agenda de reservas das quadras do dono (padrão: hoje até +30 dias)")
    public ResponseEntity<List<AgendaItemResponse>> agenda(
            @RequestParam(name = "de", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(name = "ate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @AuthenticationPrincipal Usuario dono) {
        LocalDate inicio = de != null ? de : LocalDate.now();
        LocalDate fim = ate != null ? ate : inicio.plusDays(30);
        return ResponseEntity.ok(reservaService.agenda(dono, inicio, fim));
    }

    @DeleteMapping("/reservas/{id}")
    @Operation(summary = "Dono cancela uma reserva — libera os horários e avisa os jogadores (C10)")
    public ResponseEntity<Void> cancelar(@PathVariable String id,
                                         @AuthenticationPrincipal Usuario dono) {
        reservaService.cancelarPeloDono(id, dono);
        return ResponseEntity.noContent().build();
    }
}
