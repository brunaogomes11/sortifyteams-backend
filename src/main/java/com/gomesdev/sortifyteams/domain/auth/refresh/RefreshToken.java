package com.gomesdev.sortifyteams.domain.auth.refresh;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_refresh_token", indexes = {
        @Index(name = "idx_refresh_token_hash", columnList = "token_hash"),
        @Index(name = "idx_refresh_usuario_id", columnList = "usuario_id")
})
public class RefreshToken {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "usuario_id", nullable = false, length = 26)
    private String usuarioId;

    /** SHA-256 hex do token bruto — o valor bruto nunca é persistido. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "revogado", nullable = false)
    private boolean revogado;

    /** Id do token que substituiu este (cadeia de rotação, para detecção de reuso). */
    @Column(name = "substituido_por", length = 26)
    private String substituidoPorId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    private void prePersist() {
        this.id = UlidCreator.getUlid().toString();
        this.criadoEm = LocalDateTime.now();
    }

    public RefreshToken() {
    }

    public RefreshToken(String usuarioId, String tokenHash, LocalDateTime expiraEm) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
    }

    public boolean isExpirado() {
        return !expiraEm.isAfter(LocalDateTime.now());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; }
    public boolean isRevogado() { return revogado; }
    public void setRevogado(boolean revogado) { this.revogado = revogado; }
    public String getSubstituidoPorId() { return substituidoPorId; }
    public void setSubstituidoPorId(String substituidoPorId) { this.substituidoPorId = substituidoPorId; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
