package com.gomesdev.sortifyteams.domain.racha;

import com.github.f4b6a3.ulid.UlidCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "tb_time_racha", uniqueConstraints = {
        @UniqueConstraint(name = "uk_time_racha_numero", columnNames = {"racha_id", "numero"})
})
@Schema(description = "Time gerado pelo sorteio de um racha")
public class TimeRacha {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "racha_id", nullable = false, length = 26)
    private String rachaId;

    @Column(name = "numero", nullable = false)
    private int numero;

    public TimeRacha() {
    }

    public TimeRacha(String rachaId, int numero) {
        this.rachaId = rachaId;
        this.numero = numero;
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
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeRacha timeRacha = (TimeRacha) o;
        return Objects.equals(id, timeRacha.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
