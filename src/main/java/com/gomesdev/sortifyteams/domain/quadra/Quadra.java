package com.gomesdev.sortifyteams.domain.quadra;

import com.github.f4b6a3.ulid.UlidCreator;
import com.gomesdev.sortifyteams.domain.quadra.request.QuadraRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tb_quadra", indexes = {
        @Index(name = "idx_quadra_dono", columnList = "dono_id")
})
@Schema(description = "Quadra cadastrada por um dono de quadra (Fluxo 7, 1:N — C12)")
public class Quadra {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    @Schema(description = "Identificador único ULID da quadra")
    private String id;

    @Column(name = "dono_id", nullable = false, length = 26)
    @Schema(description = "ID ULID do dono da quadra")
    private String donoId;

    @Column(name = "nome", nullable = false, length = 80)
    @Schema(description = "Nome da quadra")
    private String nome;

    @Column(name = "endereco", nullable = false)
    @Schema(description = "Endereço completo")
    private String endereco;

    @Column(name = "contato", nullable = false, length = 60)
    @Schema(description = "Contato para combinar pagamento (C7: fora do app)")
    private String contato;

    @Column(name = "ativa", nullable = false)
    @Schema(description = "Se a quadra aparece na busca de organizadores")
    private boolean ativa = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public Quadra() {
    }

    public Quadra(QuadraRequest request, String donoId) {
        this.donoId = donoId;
        this.nome = request.nome();
        this.endereco = request.endereco();
        this.contato = request.contato();
        this.ativa = request.ativa() == null || request.ativa();
    }

    public void update(QuadraRequest request) {
        this.nome = request.nome();
        this.endereco = request.endereco();
        this.contato = request.contato();
        if (request.ativa() != null) {
            this.ativa = request.ativa();
        }
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
    public String getDonoId() { return donoId; }
    public void setDonoId(String donoId) { this.donoId = donoId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
    public String getContato() { return contato; }
    public void setContato(String contato) { this.contato = contato; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quadra quadra = (Quadra) o;
        return Objects.equals(id, quadra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
