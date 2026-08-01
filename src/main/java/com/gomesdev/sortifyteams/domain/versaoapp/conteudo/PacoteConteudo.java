package com.gomesdev.sortifyteams.domain.versaoapp.conteudo;

import com.github.f4b6a3.ulid.UlidCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pacote de conteúdo (spec 002, camada OTA): um bundle JS + seus assets,
 * publicado para um {@code runtimeVersion} específico.
 *
 * <p>Invariante: no máximo <b>um ativo por runtimeVersion</b> — publicar um
 * novo desativa o anterior sem apagá-lo, que é o que permite republicar uma
 * versão anterior quando uma OTA sai ruim (C14).
 */
@Entity
@Table(name = "tb_pacote_conteudo", indexes = {
        @Index(name = "idx_pacote_conteudo_runtime", columnList = "runtime_version, ativo")
})
public class PacoteConteudo {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "uuid_manifesto", nullable = false, length = 36)
    @Schema(description = "Identidade do update no protocolo — o cliente usa para saber se já aplicou")
    private String uuidManifesto;

    @Column(name = "runtime_version", nullable = false, length = 40)
    private String runtimeVersion;

    @Column(name = "launch_asset_id", nullable = false, length = 26)
    @Schema(description = "Asset que é o bundle JS de entrada")
    private String launchAssetId;

    @Column(name = "notas", columnDefinition = "text")
    private String notas;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "publicado_em", nullable = false)
    private LocalDateTime publicadoEm;

    @Column(name = "publicado_por_id", length = 26)
    private String publicadoPorId;

    public PacoteConteudo() {
    }

    public PacoteConteudo(String runtimeVersion, String launchAssetId, String notas,
                          String publicadoPorId) {
        this.runtimeVersion = runtimeVersion;
        this.launchAssetId = launchAssetId;
        this.notas = notas;
        this.publicadoPorId = publicadoPorId;
        this.uuidManifesto = UUID.randomUUID().toString();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
        if (this.uuidManifesto == null) {
            this.uuidManifesto = UUID.randomUUID().toString();
        }
        if (this.publicadoEm == null) {
            this.publicadoEm = LocalDateTime.now();
        }
    }

    public String getId() { return id; }
    public String getUuidManifesto() { return uuidManifesto; }
    public String getRuntimeVersion() { return runtimeVersion; }
    public String getLaunchAssetId() { return launchAssetId; }
    public String getNotas() { return notas; }
    public boolean isAtivo() { return ativo; }
    public LocalDateTime getPublicadoEm() { return publicadoEm; }
    public String getPublicadoPorId() { return publicadoPorId; }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public void setNotas(String notas) { this.notas = notas; }
}
