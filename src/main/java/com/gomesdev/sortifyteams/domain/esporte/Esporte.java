package com.gomesdev.sortifyteams.domain.esporte;

import com.github.f4b6a3.ulid.UlidCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "tb_esporte")
@Schema(description = "Esporte disponível para criação de rachas")
public class Esporte {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    @Schema(description = "Identificador único ULID do esporte")
    private String id;

    @Column(name = "nome", nullable = false, unique = true, length = 40)
    @Schema(description = "Nome do esporte")
    private String nome;

    @Column(name = "icone", nullable = false, length = 40)
    @Schema(description = "Slug do ícone exibido no app")
    private String icone;

    @Column(name = "exige_goleiro", nullable = false)
    @Schema(description = "Se o esporte exige goleiro por time")
    private boolean exigeGoleiro;

    @Column(name = "jogadores_minimos_por_time", nullable = false)
    @Schema(description = "Mínimo de jogadores por time para permitir o sorteio (C6)")
    private int jogadoresMinimosPorTime;

    public Esporte() {
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getIcone() { return icone; }
    public void setIcone(String icone) { this.icone = icone; }
    public boolean isExigeGoleiro() { return exigeGoleiro; }
    public void setExigeGoleiro(boolean exigeGoleiro) { this.exigeGoleiro = exigeGoleiro; }
    public int getJogadoresMinimosPorTime() { return jogadoresMinimosPorTime; }
    public void setJogadoresMinimosPorTime(int jogadoresMinimosPorTime) { this.jogadoresMinimosPorTime = jogadoresMinimosPorTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Esporte esporte = (Esporte) o;
        return Objects.equals(id, esporte.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
