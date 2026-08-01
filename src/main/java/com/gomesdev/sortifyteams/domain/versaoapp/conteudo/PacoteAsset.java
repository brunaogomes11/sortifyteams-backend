package com.gomesdev.sortifyteams.domain.versaoapp.conteudo;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;

/**
 * Liga um {@link PacoteConteudo} aos seus {@link AssetConteudo}. A chave
 * ({@code key}) é o caminho que o cliente espera no manifesto; o asset em si é
 * compartilhado entre pacotes pelo hash.
 */
@Entity
@Table(name = "tb_pacote_asset", indexes = {
        @Index(name = "idx_pacote_asset_pacote", columnList = "pacote_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_pacote_asset_chave", columnNames = {"pacote_id", "chave"})
})
public class PacoteAsset {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "pacote_id", nullable = false, length = 26)
    private String pacoteId;

    @Column(name = "asset_id", nullable = false, length = 26)
    private String assetId;

    @Column(name = "chave", nullable = false, length = 255)
    private String chave;

    @Column(name = "extensao", length = 20)
    private String extensao;

    public PacoteAsset() {
    }

    public PacoteAsset(String pacoteId, String assetId, String chave, String extensao) {
        this.pacoteId = pacoteId;
        this.assetId = assetId;
        this.chave = chave;
        this.extensao = extensao;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
    }

    public String getId() { return id; }
    public String getPacoteId() { return pacoteId; }
    public String getAssetId() { return assetId; }
    public String getChave() { return chave; }
    public String getExtensao() { return extensao; }
}
