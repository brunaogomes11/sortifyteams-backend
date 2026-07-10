package com.gomesdev.sortifyteams.domain.racha.partida;

import com.github.f4b6a3.ulid.UlidCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tb_gol_partida")
@Schema(description = "Gol registrado numa partida do racha ao vivo")
public class GolPartida {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "partida_id", nullable = false, length = 26)
    private String partidaId;

    // Desnormalizado: permite montar o snapshot do racha inteiro em uma query.
    @Column(name = "racha_id", nullable = false, length = 26)
    private String rachaId;

    @Column(name = "time_numero", nullable = false)
    @Schema(description = "Número do time que marcou")
    private int timeNumero;

    @Column(name = "participante_id", length = 26)
    @Schema(description = "Participante autor do gol (opcional)")
    private String participanteId;

    @Column(name = "registrado_por_usuario_id", nullable = false, length = 26)
    @Schema(description = "Usuário que registrou o gol no app")
    private String registradoPorUsuarioId;

    @Column(name = "tempo_seg")
    @Schema(description = "Segundo da partida em que o gol saiu (calculado no servidor)")
    private Integer tempoSeg;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public GolPartida() {
    }

    public GolPartida(String partidaId, String rachaId, int timeNumero,
                      String participanteId, String registradoPorUsuarioId, Integer tempoSeg) {
        this.partidaId = partidaId;
        this.rachaId = rachaId;
        this.timeNumero = timeNumero;
        this.participanteId = participanteId;
        this.registradoPorUsuarioId = registradoPorUsuarioId;
        this.tempoSeg = tempoSeg;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPartidaId() { return partidaId; }
    public void setPartidaId(String partidaId) { this.partidaId = partidaId; }
    public String getRachaId() { return rachaId; }
    public void setRachaId(String rachaId) { this.rachaId = rachaId; }
    public int getTimeNumero() { return timeNumero; }
    public void setTimeNumero(int timeNumero) { this.timeNumero = timeNumero; }
    public String getParticipanteId() { return participanteId; }
    public void setParticipanteId(String participanteId) { this.participanteId = participanteId; }
    public String getRegistradoPorUsuarioId() { return registradoPorUsuarioId; }
    public void setRegistradoPorUsuarioId(String registradoPorUsuarioId) { this.registradoPorUsuarioId = registradoPorUsuarioId; }
    public Integer getTempoSeg() { return tempoSeg; }
    public void setTempoSeg(Integer tempoSeg) { this.tempoSeg = tempoSeg; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GolPartida gol = (GolPartida) o;
        return Objects.equals(id, gol.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
