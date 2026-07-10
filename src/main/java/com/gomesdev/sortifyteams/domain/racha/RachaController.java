package com.gomesdev.sortifyteams.domain.racha;

import com.gomesdev.sortifyteams.domain.racha.request.ConcluirRachaRequest;
import com.gomesdev.sortifyteams.domain.racha.request.ParticipanteRequest;
import com.gomesdev.sortifyteams.domain.racha.request.RachaConfigRequest;
import com.gomesdev.sortifyteams.domain.racha.request.RachaRequest;
import com.gomesdev.sortifyteams.domain.racha.request.TimesRequest;
import com.gomesdev.sortifyteams.domain.racha.partida.response.RachaAoVivoResponse;
import com.gomesdev.sortifyteams.domain.racha.response.RachaPublicoResponse;
import com.gomesdev.sortifyteams.domain.racha.response.RachaResponse;
import com.gomesdev.sortifyteams.domain.racha.response.RachaResumoResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rachas")
@Tag(name = "Rachas", description = "Criação e gestão de rachas (Fluxos 3 e 5)")
public class RachaController {

    private final RachaService service;

    public RachaController(RachaService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria um racha")
    public ResponseEntity<RachaResponse> criar(@Valid @RequestBody RachaRequest request,
                                               @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request, usuario));
    }

    @GetMapping
    @Operation(summary = "Lista os rachas do usuário (organizador ou participante)")
    public ResponseEntity<List<RachaResumoResponse>> listar(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.listarDoUsuario(usuario));
    }

    @GetMapping("/publicos")
    @Operation(summary = "Lista rachas públicos próximos (por GPS) ou de uma cidade")
    public ResponseEntity<List<RachaPublicoResponse>> listarPublicos(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) Double raioKm,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.listarPublicos(lat, lon, cidade, raioKm, usuario));
    }

    @GetMapping("/publicos/cidades")
    @Operation(summary = "Cidades com rachas públicos abertos (filtro sem GPS)")
    public ResponseEntity<List<String>> listarCidadesPublicas() {
        return ResponseEntity.ok(service.listarCidadesPublicas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um racha (participantes, times do sorteio)")
    public ResponseEntity<RachaResponse> detalhar(@PathVariable String id,
                                                  @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.detalhar(id, usuario));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancela um racha (C10)")
    public ResponseEntity<Void> cancelar(@PathVariable String id,
                                         @AuthenticationPrincipal Usuario usuario) {
        service.cancelar(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/participantes")
    @Operation(summary = "Adiciona um jogador (avulso ou usuário cadastrado — C3)")
    public ResponseEntity<RachaResponse> adicionarParticipante(@PathVariable String id,
                                                               @Valid @RequestBody ParticipanteRequest request,
                                                               @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarParticipante(id, request, usuario));
    }

    @DeleteMapping("/{id}/participantes/me")
    @Operation(summary = "Sai do racha (participante — Fluxo 5)")
    public ResponseEntity<Void> sair(@PathVariable String id,
                                     @AuthenticationPrincipal Usuario usuario) {
        service.sairDoRacha(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/participantes/{participanteId}")
    @Operation(summary = "Remove um jogador do racha")
    public ResponseEntity<RachaResponse> removerParticipante(@PathVariable String id,
                                                             @PathVariable String participanteId,
                                                             @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.removerParticipante(id, participanteId, usuario));
    }

    @PatchMapping("/{id}/config")
    @Operation(summary = "Edita a configuração de nível técnico do racha (Fluxo 5)")
    public ResponseEntity<RachaResponse> atualizarConfiguracao(@PathVariable String id,
                                                               @Valid @RequestBody RachaConfigRequest request,
                                                               @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.atualizarConfiguracao(id, request, usuario));
    }

    @PostMapping("/{id}/sorteio")
    @Operation(summary = "Sorteia (ou re-sorteia) os times (FR-007)")
    public ResponseEntity<RachaResponse> sortear(@PathVariable String id,
                                                 @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.sortear(id, usuario));
    }

    @PatchMapping("/{id}/times")
    @Operation(summary = "Ajusta manualmente os times sorteados (troca jogadores de time — Fluxo 5)")
    public ResponseEntity<RachaResponse> atualizarTimes(@PathVariable String id,
                                                        @Valid @RequestBody TimesRequest request,
                                                        @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.atualizarTimes(id, request, usuario));
    }

    @PostMapping("/{id}/iniciar")
    @Operation(summary = "Inicia o racha ao vivo (EM_ANDAMENTO) — habilita partidas e gols")
    public ResponseEntity<RachaAoVivoResponse> iniciar(@PathVariable String id,
                                                       @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.iniciar(id, usuario));
    }

    @PostMapping("/{id}/concluir")
    @Operation(summary = "Conclui o racha, registrando a duração da partida (C1)")
    public ResponseEntity<RachaResponse> concluir(@PathVariable String id,
                                                  @RequestBody(required = false) @Valid ConcluirRachaRequest request,
                                                  @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.concluir(id, request, usuario));
    }
}
