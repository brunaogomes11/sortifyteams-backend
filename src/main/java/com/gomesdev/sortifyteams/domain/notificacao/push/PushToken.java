package com.gomesdev.sortifyteams.domain.notificacao.push;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_push_token", uniqueConstraints = {
        @UniqueConstraint(name = "uk_push_token_usuario", columnNames = {"usuario_id", "expo_token"})
})
public class PushToken {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "usuario_id", nullable = false, length = 26)
    private String usuarioId;

    @Column(name = "expo_token", nullable = false, length = 120)
    private String expoToken;

    @Column(name = "plataforma", nullable = false, length = 10)
    private String plataforma;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    public PushToken() {
    }

    public PushToken(String usuarioId, String expoToken, String plataforma) {
        this.usuarioId = usuarioId;
        this.expoToken = expoToken;
        this.plataforma = plataforma;
    }

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
        this.atualizadoEm = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getUsuarioId() { return usuarioId; }
    public String getExpoToken() { return expoToken; }
    public String getPlataforma() { return plataforma; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public void setExpoToken(String expoToken) { this.expoToken = expoToken; }
    public void setPlataforma(String plataforma) { this.plataforma = plataforma; }
}
