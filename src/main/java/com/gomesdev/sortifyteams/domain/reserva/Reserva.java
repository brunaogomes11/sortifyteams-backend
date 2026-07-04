package com.gomesdev.sortifyteams.domain.reserva;

import com.github.f4b6a3.ulid.UlidCreator;
import com.gomesdev.sortifyteams.enums.StatusReservaEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tb_reserva", indexes = {
        @Index(name = "idx_reserva_quadra_data", columnList = "quadra_id, data"),
        @Index(name = "idx_reserva_racha", columnList = "racha_id")
})
@Schema(description = "Reserva de quadra feita por um organizador para um racha (Fluxo 4). Pagamento fora do app (C7).")
public class Reserva {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "quadra_id", nullable = false, length = 26)
    private String quadraId;

    @Column(name = "racha_id", nullable = false, length = 26)
    private String rachaId;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusReservaEnum status = StatusReservaEnum.CONFIRMADA;

    @Column(name = "preco_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoTotal;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public Reserva() {
    }

    public Reserva(String quadraId, String rachaId, LocalDate data, BigDecimal precoTotal) {
        this.quadraId = quadraId;
        this.rachaId = rachaId;
        this.data = data;
        this.precoTotal = precoTotal;
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
    public String getQuadraId() { return quadraId; }
    public void setQuadraId(String quadraId) { this.quadraId = quadraId; }
    public String getRachaId() { return rachaId; }
    public void setRachaId(String rachaId) { this.rachaId = rachaId; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public StatusReservaEnum getStatus() { return status; }
    public void setStatus(StatusReservaEnum status) { this.status = status; }
    public BigDecimal getPrecoTotal() { return precoTotal; }
    public void setPrecoTotal(BigDecimal precoTotal) { this.precoTotal = precoTotal; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reserva reserva = (Reserva) o;
        return Objects.equals(id, reserva.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
