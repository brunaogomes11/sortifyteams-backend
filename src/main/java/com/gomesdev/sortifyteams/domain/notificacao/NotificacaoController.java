package com.gomesdev.sortifyteams.domain.notificacao;

import com.gomesdev.sortifyteams.domain.notificacao.request.PushTokenRequest;
import com.gomesdev.sortifyteams.domain.notificacao.response.NotificacaoResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notificacoes")
@Tag(name = "Notificações", description = "Central de notificações in-app (D5)")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista as notificações do usuário (50 mais recentes)")
    public ResponseEntity<List<NotificacaoResponse>> listar(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.listar(usuario));
    }

    @PutMapping("/{id}/lida")
    @Operation(summary = "Marca uma notificação como lida")
    public ResponseEntity<Void> marcarLida(@PathVariable String id,
                                           @AuthenticationPrincipal Usuario usuario) {
        service.marcarLida(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/push-token")
    @Operation(summary = "Registra o Expo push token do dispositivo")
    public ResponseEntity<Void> registrarPushToken(@Valid @RequestBody PushTokenRequest request,
                                                   @AuthenticationPrincipal Usuario usuario) {
        service.registrarPushToken(usuario, request.expoToken(), request.plataforma());
        return ResponseEntity.noContent().build();
    }
}
