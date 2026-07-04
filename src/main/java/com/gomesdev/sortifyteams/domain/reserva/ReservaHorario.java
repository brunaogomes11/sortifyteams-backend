package com.gomesdev.sortifyteams.domain.reserva;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Slot reservado (horário da grade + data). A UNIQUE (quadra_horario_id, data)
 * é a regra crítica C8/FR-009: o segundo INSERT concorrente viola a constraint
 * e vira HTTP 409. O cancelamento REMOVE estas linhas (libera o horário);
 * o histórico permanece em tb_reserva via status.
 */
@Entity
@Table(name = "tb_reserva_horario", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reserva_horario_slot", columnNames = {"quadra_horario_id", "data"})
})
public class ReservaHorario {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "reserva_id", nullable = false, length = 26)
    private String reservaId;

    @Column(name = "quadra_horario_id", nullable = false, length = 26)
    private String quadraHorarioId;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    public ReservaHorario() {
    }

    public ReservaHorario(String reservaId, String quadraHorarioId, LocalDate data) {
        this.reservaId = reservaId;
        this.quadraHorarioId = quadraHorarioId;
        this.data = data;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
    }

    public String getId() { return id; }
    public String getReservaId() { return reservaId; }
    public String getQuadraHorarioId() { return quadraHorarioId; }
    public LocalDate getData() { return data; }
    public void setReservaId(String reservaId) { this.reservaId = reservaId; }
    public void setQuadraHorarioId(String quadraHorarioId) { this.quadraHorarioId = quadraHorarioId; }
    public void setData(LocalDate data) { this.data = data; }
}
