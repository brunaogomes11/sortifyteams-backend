package com.gomesdev.sortifyteams.domain.notificacao;

import com.github.f4b6a3.ulid.UlidCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tb_notificacao", indexes = {
        @Index(name = "idx_notificacao_usuario", columnList = "usuario_id, lida")
})
@Schema(description = "Notificação in-app do usuário (fonte de verdade — D5)")
public class Notificacao {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "usuario_id", nullable = false, length = 26)
    private String usuarioId;

    @Column(name = "tipo", nullable = false, length = 40)
    @Schema(description = "Tipo da notificação (DONO_APROVADO, DONO_REJEITADO, RESERVA_CANCELADA, ...)")
    private String tipo;

    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "corpo", length = 500)
    private String corpo;

    @Column(name = "lida", nullable = false)
    private boolean lida;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    public Notificacao() {
    }

    public Notificacao(String usuarioId, String tipo, String titulo, String corpo) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.titulo = titulo;
        this.corpo = corpo;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
        if (this.criadaEm == null) {
            this.criadaEm = LocalDateTime.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getCorpo() { return corpo; }
    public void setCorpo(String corpo) { this.corpo = corpo; }
    public boolean isLida() { return lida; }
    public void setLida(boolean lida) { this.lida = lida; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Notificacao that = (Notificacao) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
