package com.gomesdev.sortifyteams.domain.auth;

import com.gomesdev.sortifyteams.domain.auth.refresh.RefreshTokenService;
import com.gomesdev.sortifyteams.domain.auth.request.LoginRequest;
import com.gomesdev.sortifyteams.domain.auth.request.RegisterRequest;
import com.gomesdev.sortifyteams.domain.auth.request.ReenviarSolicitacaoRequest;
import com.gomesdev.sortifyteams.domain.auth.response.AuthTokens;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository;
import com.gomesdev.sortifyteams.enums.RoleEnum;
import com.gomesdev.sortifyteams.enums.StatusUsuarioEnum;
import com.gomesdev.sortifyteams.security.JwtService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UsuarioRepository usuarioRepository,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService) {
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthTokens register(RegisterRequest request) {
        if (request.role() == RoleEnum.ADMIN) {
            throw new IllegalArgumentException("Perfil ADMIN não pode ser registrado.");
        }
        if (usuarioRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Nome de usuário já cadastrado.");
        }
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado.");
        }

        Usuario usuario = new Usuario(request, passwordEncoder.encode(request.senha()));
        usuarioRepository.save(usuario);

        // Dono de quadra nasce PENDENTE e só recebe token após aprovação (FR-003).
        if (usuario.getStatus() != StatusUsuarioEnum.APROVADO) {
            return new AuthTokens(null, null, usuario);
        }
        return issueTokens(usuario);
    }

    public AuthTokens login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.senha())
        );

        Usuario usuario = usuarioRepository.findByUsername(request.username()).orElseThrow();
        garantirAprovado(usuario);
        return issueTokens(usuario);
    }

    /** Rotaciona o refresh token e emite um novo par de tokens. */
    public AuthTokens refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken);
        Usuario usuario = usuarioRepository.findById(rotation.usuarioId()).orElseThrow();
        garantirAprovado(usuario);
        String accessToken = jwtService.generateToken(usuario);
        return new AuthTokens(accessToken, rotation.rawToken(), usuario);
    }

    /** Revoga o refresh token (logout). */
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    /** Dono de quadra REJEITADO pode reenviar a solicitação — volta a PENDENTE (C13). */
    @Transactional
    public void reenviarSolicitacao(ReenviarSolicitacaoRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas."));
        if (!passwordEncoder.matches(request.senha(), usuario.getSenha())) {
            throw new BadCredentialsException("Credenciais inválidas.");
        }
        if (usuario.getStatus() != StatusUsuarioEnum.REJEITADO) {
            throw new IllegalArgumentException("Apenas cadastros rejeitados podem reenviar a solicitação.");
        }
        usuario.setStatus(StatusUsuarioEnum.PENDENTE);
        usuarioRepository.save(usuario);
    }

    private void garantirAprovado(Usuario usuario) {
        switch (usuario.getStatus()) {
            case PENDENTE -> throw new AccessDeniedException(
                    "Cadastro aguardando aprovação do administrador.");
            case REJEITADO -> throw new AccessDeniedException(
                    "Cadastro rejeitado. Você pode reenviar a solicitação.");
            case APROVADO -> { /* segue */ }
        }
    }

    private AuthTokens issueTokens(Usuario usuario) {
        String accessToken = jwtService.generateToken(usuario);
        String refreshToken = refreshTokenService.issue(usuario.getId());
        return new AuthTokens(accessToken, refreshToken, usuario);
    }
}
