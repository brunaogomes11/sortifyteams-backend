package com.gomesdev.sortifyteams.domain.auth.refresh;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final long expirationMs;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.refresh-token.expiration-ms:2592000000}") long expirationMs) {
        this.repository = repository;
        this.expirationMs = expirationMs;
    }

    /** Resultado de uma rotação: novo token bruto (vai pro corpo da resposta) + dono. */
    public record RotationResult(String rawToken, String usuarioId) {}

    /** Emite um novo refresh token e devolve o valor bruto (somente o hash é persistido). */
    @Transactional
    public String issue(String usuarioId) {
        String rawToken = generateRawToken();
        LocalDateTime expiraEm = LocalDateTime.now().plusNanos(expirationMs * 1_000_000);
        repository.save(new RefreshToken(usuarioId, hash(rawToken), expiraEm));
        return rawToken;
    }

    /**
     * Valida e rotaciona o refresh token. Se o token apresentado já estava revogado,
     * trata como reuso (possível roubo) e revoga toda a cadeia do usuário.
     * noRollbackFor: a revogação em massa precisa ser persistida mesmo com a
     * exceção de reuso — rollback desfaria a própria proteção.
     */
    @Transactional(noRollbackFor = IllegalStateException.class)
    public RotationResult rotate(String rawToken) {
        RefreshToken atual = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token inválido"));

        if (atual.isRevogado()) {
            // Reuso de um token já rotacionado → revoga tudo do usuário por segurança.
            repository.revogarTodosDoUsuario(atual.getUsuarioId());
            throw new IllegalStateException("Refresh token reutilizado — sessões revogadas");
        }
        if (atual.isExpirado()) {
            throw new IllegalArgumentException("Refresh token expirado");
        }

        String novoRaw = generateRawToken();
        LocalDateTime expiraEm = LocalDateTime.now().plusNanos(expirationMs * 1_000_000);
        RefreshToken rotacionado = repository.save(new RefreshToken(atual.getUsuarioId(), hash(novoRaw), expiraEm));

        atual.setRevogado(true);
        atual.setSubstituidoPorId(rotacionado.getId());
        repository.save(atual);

        return new RotationResult(novoRaw, atual.getUsuarioId());
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevogado(true);
            repository.save(token);
        });
    }

    @Transactional
    public void revokeAllForUser(String usuarioId) {
        repository.revogarTodosDoUsuario(usuarioId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return urlEncoder.encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
