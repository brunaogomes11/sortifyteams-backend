package com.gomesdev.sortifyteams.domain.quadra;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;

@Entity
@Table(name = "tb_quadra_foto", indexes = {
        @Index(name = "idx_quadra_foto_quadra", columnList = "quadra_id")
})
public class QuadraFoto {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "quadra_id", nullable = false, length = 26)
    private String quadraId;

    /** Chave no StorageService (ex.: quadras/<id>/<uuid>.jpg). */
    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    public QuadraFoto() {
    }

    public QuadraFoto(String quadraId, String path, int ordem) {
        this.quadraId = quadraId;
        this.path = path;
        this.ordem = ordem;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
    }

    public String getId() { return id; }
    public String getQuadraId() { return quadraId; }
    public String getPath() { return path; }
    public int getOrdem() { return ordem; }
    public void setQuadraId(String quadraId) { this.quadraId = quadraId; }
    public void setPath(String path) { this.path = path; }
    public void setOrdem(int ordem) { this.ordem = ordem; }
}
