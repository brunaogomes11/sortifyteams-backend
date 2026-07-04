package com.gomesdev.sortifyteams.domain.quadra;

import com.gomesdev.sortifyteams.domain.quadra.response.QuadraPublicaResponse;
import com.gomesdev.sortifyteams.domain.reserva.ReservaService;
import com.gomesdev.sortifyteams.domain.reserva.response.DisponibilidadeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/quadras")
@Tag(name = "Quadras", description = "Busca pública de quadras para reserva (Fluxo 4)")
public class QuadraPublicaController {

    private final QuadraPublicaService quadraService;
    private final ReservaService reservaService;

    public QuadraPublicaController(QuadraPublicaService quadraService, ReservaService reservaService) {
        this.quadraService = quadraService;
        this.reservaService = reservaService;
    }

    @GetMapping
    @Operation(summary = "Lista quadras ativas, com filtro por nome/endereço e paginação")
    public ResponseEntity<Page<QuadraPublicaResponse>> listar(
            @RequestParam(name = "busca", required = false) String busca,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(quadraService.listar(busca, PageRequest.of(page, Math.min(size, 50))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha uma quadra (fotos, endereço, contato, grade)")
    public ResponseEntity<QuadraPublicaResponse> detalhar(@PathVariable String id) {
        return ResponseEntity.ok(quadraService.detalhar(id));
    }

    @GetMapping("/{id}/disponibilidade")
    @Operation(summary = "Disponibilidade dos horários da quadra numa data (grade − reservados)")
    public ResponseEntity<DisponibilidadeResponse> disponibilidade(
            @PathVariable String id,
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(reservaService.disponibilidade(id, data));
    }
}
