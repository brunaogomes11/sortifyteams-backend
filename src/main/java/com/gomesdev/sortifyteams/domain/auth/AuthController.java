package com.gomesdev.sortifyteams.domain.auth;

import com.gomesdev.sortifyteams.config.ErrorDetails;
import com.gomesdev.sortifyteams.domain.auth.request.LoginRequest;
import com.gomesdev.sortifyteams.domain.auth.request.ReenviarSolicitacaoRequest;
import com.gomesdev.sortifyteams.domain.auth.request.RefreshRequest;
import com.gomesdev.sortifyteams.domain.auth.request.RegisterRequest;
import com.gomesdev.sortifyteams.domain.auth.response.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Endpoints de autenticação JWT da API mobile")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    @Operation(summary = "Registro de novo usuário",
            description = "Cria uma conta JOGADOR (liberada) ou DONO_QUADRA (fica PENDENTE até aprovação do admin — tokens nulos na resposta).")
    @ApiResponse(responseCode = "201", description = "Usuário registrado",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Dados inválidos ou username/e-mail já cadastrado",
            content = @Content(schema = @Schema(implementation = ErrorDetails.ErrorResponse.class)))
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login com usuário e senha",
            description = "Autentica e retorna access token (JWT) + refresh token. DONO_QUADRA pendente/rejeitado recebe 403 com o motivo.")
    @ApiResponse(responseCode = "200", description = "Autenticado com sucesso",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas",
            content = @Content(schema = @Schema(implementation = ErrorDetails.ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Cadastro pendente ou rejeitado",
            content = @Content(schema = @Schema(implementation = ErrorDetails.ErrorResponse.class)))
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(new AuthResponse(authService.login(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o access token",
            description = "Usa o refresh token para emitir um novo access token e rotacionar o refresh token. Reuso de token antigo revoga todas as sessões.")
    @ApiResponse(responseCode = "200", description = "Token renovado",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "401", description = "Refresh token inválido, reutilizado ou expirado")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        try {
            return ResponseEntity.ok(new AuthResponse(authService.refresh(request.refreshToken())));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Token inválido/reutilizado/expirado → 401 para o app refazer o login.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoga o refresh token informado.")
    @ApiResponse(responseCode = "204", description = "Logout efetuado")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request != null ? request.refreshToken() : null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reenviar-solicitacao")
    @Operation(summary = "Reenvia solicitação de dono de quadra rejeitado",
            description = "Valida as credenciais e volta o status do cadastro REJEITADO para PENDENTE (C13).")
    @ApiResponse(responseCode = "204", description = "Solicitação reenviada")
    @ApiResponse(responseCode = "400", description = "Cadastro não está rejeitado",
            content = @Content(schema = @Schema(implementation = ErrorDetails.ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas",
            content = @Content(schema = @Schema(implementation = ErrorDetails.ErrorResponse.class)))
    public ResponseEntity<Void> reenviarSolicitacao(@Valid @RequestBody ReenviarSolicitacaoRequest request) {
        authService.reenviarSolicitacao(request);
        return ResponseEntity.noContent().build();
    }
}
