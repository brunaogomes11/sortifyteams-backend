package com.gomesdev.sortifyteams.domain.racha.partida;

import com.github.f4b6a3.ulid.UlidCreator;
import com.gomesdev.sortifyteams.enums.StatusPartidaEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Partida disputada dentro de um racha ao vivo (dinâmica "vencedor fica").
 * Referencia os times pelo NÚMERO (não pelo id de TimeRacha): o re-sorteio
 * recria os TimeRacha com ids novos, mas só roda com o racha ABERTO — ao
 * entrar EM_ANDAMENTO os números ficam congelados.
 */
@Entity
@Table(name = "tb_partida_racha")
@Schema(description = "Partida de um racha ao vivo")
public class PartidaRacha {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "racha_id", nullable = false, length = 26)
    private String rachaId;

    @Column(name = "time_numero_a", nullable = false)
    @Schema(description = "Número do time do lado A")
    private int timeNumeroA;

    @Column(name = "time_numero_b", nullable = false)
    @Schema(description = "Número do time do lado B")
    private int timeNumeroB;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusPartidaEnum status = StatusPartidaEnum.EM_ANDAMENTO;

    @Column(name = "duracao_prevista_seg")
    @Schema(description = "Duração prevista da partida em segundos (o cronômetro do app conta a partir daqui)")
    private Integer duracaoPrevistaSeg;

    @Column(name = "iniciada_em", nullable = false)
    private LocalDateTime iniciadaEm;

    @Column(name = "encerrada_em")
    private LocalDateTime encerradaEm;

    @Column(name = "vencedor_time_numero")
    @Schema(description = "Número do time vencedor, resolvido no encerramento (placar ou critério de empate); "
            + "nulo enquanto em andamento ou em empate sem resolução")
    private Integer vencedorTimeNumero;

    public PartidaRacha() {
    }

    public PartidaRacha(String rachaId, int timeNumeroA, int timeNumeroB, Integer duracaoPrevistaSeg) {
        this.rachaId = rachaId;
        this.timeNumeroA = timeNumeroA;
        this.timeNumeroB = timeNumeroB;
        this.duracaoPrevistaSeg = duracaoPrevistaSeg;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
        if (this.iniciadaEm == null) {
            this.iniciadaEm = LocalDateTime.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRachaId() { return rachaId; }
    public void setRachaId(String rachaId) { this.rachaId = rachaId; }
    public int getTimeNumeroA() { return timeNumeroA; }
    public void setTimeNumeroA(int timeNumeroA) { this.timeNumeroA = timeNumeroA; }
    public int getTimeNumeroB() { return timeNumeroB; }
    public void setTimeNumeroB(int timeNumeroB) { this.timeNumeroB = timeNumeroB; }
    public StatusPartidaEnum getStatus() { return status; }
    public void setStatus(StatusPartidaEnum status) { this.status = status; }
    public Integer getDuracaoPrevistaSeg() { return duracaoPrevistaSeg; }
    public void setDuracaoPrevistaSeg(Integer duracaoPrevistaSeg) { this.duracaoPrevistaSeg = duracaoPrevistaSeg; }
    public LocalDateTime getIniciadaEm() { return iniciadaEm; }
    public void setIniciadaEm(LocalDateTime iniciadaEm) { this.iniciadaEm = iniciadaEm; }
    public LocalDateTime getEncerradaEm() { return encerradaEm; }
    public void setEncerradaEm(LocalDateTime encerradaEm) { this.encerradaEm = encerradaEm; }
    public Integer getVencedorTimeNumero() { return vencedorTimeNumero; }
    public void setVencedorTimeNumero(Integer vencedorTimeNumero) { this.vencedorTimeNumero = vencedorTimeNumero; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartidaRacha partida = (PartidaRacha) o;
        return Objects.equals(id, partida.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
