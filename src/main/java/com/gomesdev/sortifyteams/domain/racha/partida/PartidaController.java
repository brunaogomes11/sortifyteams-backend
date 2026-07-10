package com.gomesdev.sortifyteams.domain.racha.partida;

import com.gomesdev.sortifyteams.domain.racha.partida.request.EncerrarPartidaRequest;
import com.gomesdev.sortifyteams.domain.racha.partida.request.GolRequest;
import com.gomesdev.sortifyteams.domain.racha.partida.request.PartidaRequest;
import com.gomesdev.sortifyteams.domain.racha.partida.response.RachaAoVivoResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/rachas/{rachaId}")
@Tag(name = "Partidas ao vivo", description = "Placar, gols e partidas do racha em andamento. "
        + "Além do GET, o mesmo snapshot (RachaAoVivoResponse) é publicado via WebSocket/STOMP "
        + "no tópico /topic/rachas/{rachaId} (endpoint /ws, JWT no header Authorization do frame CONNECT).")
public class PartidaController {

    private final PartidaService service;

    public PartidaController(PartidaService service) {
        this.service = service;
    }

    @GetMapping("/ao-vivo")
    @Operation(summary = "Snapshot ao vivo do racha (partida atual, placar, gols e histórico de partidas)")
    public ResponseEntity<RachaAoVivoResponse> aoVivo(@PathVariable String rachaId,
                                                      @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.aoVivo(rachaId, usuario));
    }

    @PostMapping("/partidas")
    @Operation(summary = "Começa uma partida (dois times em quadra — organizador)")
    public ResponseEntity<RachaAoVivoResponse> criarPartida(@PathVariable String rachaId,
                                                            @Valid @RequestBody PartidaRequest request,
                                                            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarPartida(rachaId, request, usuario));
    }

    @PostMapping("/partidas/{partidaId}/encerrar")
    @Operation(summary = "Encerra a partida; em empate decidido nos pênaltis, informe o vencedor no body")
    public ResponseEntity<RachaAoVivoResponse> encerrarPartida(@PathVariable String rachaId,
                                                               @PathVariable String partidaId,
                                                               @RequestBody(required = false) @Valid EncerrarPartidaRequest request,
                                                               @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.encerrarPartida(rachaId, partidaId, request, usuario));
    }

    @PostMapping("/partidas/{partidaId}/gols")
    @Operation(summary = "Registra um gol (+1 no time; autor opcional) — qualquer membro do racha")
    public ResponseEntity<RachaAoVivoResponse> registrarGol(@PathVariable String rachaId,
                                                            @PathVariable String partidaId,
                                                            @Valid @RequestBody GolRequest request,
                                                            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrarGol(rachaId, partidaId, request, usuario));
    }

    @DeleteMapping("/gols/{golId}")
    @Operation(summary = "Remove um gol registrado por engano (organizador ou quem registrou)")
    public ResponseEntity<RachaAoVivoResponse> removerGol(@PathVariable String rachaId,
                                                          @PathVariable String golId,
                                                          @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.removerGol(rachaId, golId, usuario));
    }
}
