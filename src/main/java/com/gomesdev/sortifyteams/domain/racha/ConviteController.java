package com.gomesdev.sortifyteams.domain.racha;

import com.gomesdev.sortifyteams.domain.racha.request.EntrarConviteRequest;
import com.gomesdev.sortifyteams.domain.racha.response.ConviteResponse;
import com.gomesdev.sortifyteams.domain.racha.response.RachaResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/convites")
@Tag(name = "Convites", description = "Entrada em rachas via link de convite (C9)")
public class ConviteController {

    private final RachaService rachaService;

    public ConviteController(RachaService rachaService) {
        this.rachaService = rachaService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Prévia do racha do convite (dados mínimos — FR-016)")
    public ResponseEntity<ConviteResponse> detalhar(@PathVariable String token,
                                                    @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(rachaService.detalharConvite(token, usuario));
    }

    @PostMapping("/{token}/entrar")
    @Operation(summary = "Entra no racha pelo convite, respeitando o limite de vagas")
    public ResponseEntity<RachaResponse> entrar(@PathVariable String token,
                                                @Valid @RequestBody EntrarConviteRequest request,
                                                @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rachaService.entrarPorConvite(token, request, usuario));
    }
}
