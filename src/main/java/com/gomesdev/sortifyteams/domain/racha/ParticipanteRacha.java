package com.gomesdev.sortifyteams.domain.racha;

import com.github.f4b6a3.ulid.UlidCreator;
import com.gomesdev.sortifyteams.domain.racha.request.ParticipanteRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "tb_participante_racha", uniqueConstraints = {
        @UniqueConstraint(name = "uk_participante_racha_usuario", columnNames = {"racha_id", "usuario_id"})
})
@Schema(description = "Jogador de um racha — usuário cadastrado ou nome avulso (C3)")
public class ParticipanteRacha {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "racha_id", nullable = false, length = 26)
    private String rachaId;

    @Column(name = "usuario_id", length = 26)
    @Schema(description = "ID do usuário cadastrado (nulo para jogador avulso)")
    private String usuarioId;

    @Column(name = "nome_avulso", length = 80)
    @Schema(description = "Nome digitado pelo organizador (nulo para usuário cadastrado)")
    private String nomeAvulso;

    @Column(name = "nivel_tecnico", nullable = false)
    @Schema(description = "Nível técnico de 1 a 5")
    private int nivelTecnico;

    @Column(name = "e_goleiro", nullable = false)
    @Schema(description = "Se o jogador é goleiro (esportes que exigem)")
    private boolean eGoleiro;

    @Column(name = "time_id", length = 26)
    @Schema(description = "Time atribuído pelo sorteio")
    private String timeId;

    public ParticipanteRacha() {
    }

    public ParticipanteRacha(ParticipanteRequest request, String rachaId) {
        this.rachaId = rachaId;
        this.usuarioId = request.usuarioId();
        this.nomeAvulso = request.nomeAvulso();
        this.nivelTecnico = request.nivelTecnico();
        this.eGoleiro = Boolean.TRUE.equals(request.eGoleiro());
    }

    /** Entrada de usuário cadastrado via link de convite (C9). */
    public ParticipanteRacha(String rachaId, String usuarioId, int nivelTecnico, boolean eGoleiro) {
        this.rachaId = rachaId;
        this.usuarioId = usuarioId;
        this.nivelTecnico = nivelTecnico;
        this.eGoleiro = eGoleiro;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRachaId() { return rachaId; }
    public void setRachaId(String rachaId) { this.rachaId = rachaId; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getNomeAvulso() { return nomeAvulso; }
    public void setNomeAvulso(String nomeAvulso) { this.nomeAvulso = nomeAvulso; }
    public int getNivelTecnico() { return nivelTecnico; }
    public void setNivelTecnico(int nivelTecnico) { this.nivelTecnico = nivelTecnico; }
    public boolean isEGoleiro() { return eGoleiro; }
    public void setEGoleiro(boolean eGoleiro) { this.eGoleiro = eGoleiro; }
    public String getTimeId() { return timeId; }
    public void setTimeId(String timeId) { this.timeId = timeId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParticipanteRacha that = (ParticipanteRacha) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
