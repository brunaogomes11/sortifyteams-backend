package com.gomesdev.sortifyteams.domain.quadra;

import com.github.f4b6a3.ulid.UlidCreator;
import com.gomesdev.sortifyteams.domain.quadra.request.HorarioRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Slot da grade semanal recorrente de uma quadra, com preço (Fluxo 7). */
@Entity
@Table(name = "tb_quadra_horario", indexes = {
        @Index(name = "idx_quadra_horario_quadra", columnList = "quadra_id, dia_semana")
})
public class QuadraHorario {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "quadra_id", nullable = false, length = 26)
    private String quadraId;

    /** 0 = domingo ... 6 = sábado. */
    @Column(name = "dia_semana", nullable = false)
    @Schema(description = "Dia da semana: 0=domingo ... 6=sábado")
    private int diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(name = "preco", nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    public QuadraHorario() {
    }

    public QuadraHorario(HorarioRequest request, String quadraId) {
        this.quadraId = quadraId;
        this.diaSemana = request.diaSemana();
        this.horaInicio = request.horaInicio();
        this.horaFim = request.horaFim();
        this.preco = request.preco();
    }

    /** Slot de 1 hora expandido a partir de uma faixa maior (FIX 15 — cada reserva dura 1 hora). */
    public QuadraHorario(String quadraId, int diaSemana, LocalTime horaInicio, LocalTime horaFim, BigDecimal preco) {
        this.quadraId = quadraId;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.preco = preco;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
    }

    public String getId() { return id; }
    public String getQuadraId() { return quadraId; }
    public int getDiaSemana() { return diaSemana; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public BigDecimal getPreco() { return preco; }
    public void setQuadraId(String quadraId) { this.quadraId = quadraId; }
    public void setDiaSemana(int diaSemana) { this.diaSemana = diaSemana; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public void setHoraFim(LocalTime horaFim) { this.horaFim = horaFim; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
}
