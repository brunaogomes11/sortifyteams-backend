package com.gomesdev.sortifyteams.domain.quadra;

import com.gomesdev.sortifyteams.domain.quadra.request.HorariosRequest;
import com.gomesdev.sortifyteams.domain.quadra.request.QuadraRequest;
import com.gomesdev.sortifyteams.domain.quadra.response.QuadraResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/dono/quadras")
@PreAuthorize("hasRole('DONO_QUADRA')")
@Tag(name = "Quadras do Dono", description = "Gestão de quadras do dono aprovado (Fluxo 7)")
public class QuadraDonoController {

    private final QuadraDonoService service;

    public QuadraDonoController(QuadraDonoService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cadastra uma quadra")
    public ResponseEntity<QuadraResponse> criar(@Valid @RequestBody QuadraRequest request,
                                                @AuthenticationPrincipal Usuario dono) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request, dono));
    }

    @GetMapping
    @Operation(summary = "Lista as quadras do dono (1:N — C12)")
    public ResponseEntity<List<QuadraResponse>> listar(@AuthenticationPrincipal Usuario dono) {
        return ResponseEntity.ok(service.listarDoDono(dono));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha uma quadra do dono")
    public ResponseEntity<QuadraResponse> detalhar(@PathVariable String id,
                                                   @AuthenticationPrincipal Usuario dono) {
        return ResponseEntity.ok(service.detalhar(id, dono));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados da quadra")
    public ResponseEntity<QuadraResponse> atualizar(@PathVariable String id,
                                                    @Valid @RequestBody QuadraRequest request,
                                                    @AuthenticationPrincipal Usuario dono) {
        return ResponseEntity.ok(service.atualizar(id, request, dono));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativa a quadra (some da busca; histórico preservado)")
    public ResponseEntity<Void> excluir(@PathVariable String id,
                                        @AuthenticationPrincipal Usuario dono) {
        service.excluir(id, dono);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/horarios")
    @Operation(summary = "Define a grade semanal recorrente (substitui a anterior)")
    public ResponseEntity<QuadraResponse> definirHorarios(@PathVariable String id,
                                                          @Valid @RequestBody HorariosRequest request,
                                                          @AuthenticationPrincipal Usuario dono) {
        return ResponseEntity.ok(service.definirHorarios(id, request, dono));
    }

    @PostMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Adiciona uma foto à quadra")
    public ResponseEntity<QuadraResponse> adicionarFoto(@PathVariable String id,
                                                        @RequestPart("arquivo") MultipartFile arquivo,
                                                        @AuthenticationPrincipal Usuario dono) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarFoto(id, arquivo, dono));
    }

    @DeleteMapping("/{id}/fotos/{fotoId}")
    @Operation(summary = "Remove uma foto da quadra")
    public ResponseEntity<QuadraResponse> removerFoto(@PathVariable String id,
                                                      @PathVariable String fotoId,
                                                      @AuthenticationPrincipal Usuario dono) {
        return ResponseEntity.ok(service.removerFoto(id, fotoId, dono));
    }
}
