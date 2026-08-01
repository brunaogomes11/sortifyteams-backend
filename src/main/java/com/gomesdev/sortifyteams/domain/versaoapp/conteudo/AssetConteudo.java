package com.gomesdev.sortifyteams.domain.versaoapp.conteudo;

import com.github.f4b6a3.ulid.UlidCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Arquivo de um pacote de conteúdo (bundle JS ou asset), endereçado por
 * conteúdo (spec 002, FR-004).
 *
 * <p>O {@code hash} é único: dois pacotes que compartilham a mesma imagem
 * apontam para a mesma linha. É isso que faz o app baixar <b>só o que mudou</b>
 * — o cliente já tem o asset em disco e o identifica pelo mesmo hash.
 *
 * <p>Assim como no APK, a coluna binária fica fora do mapeamento JPA
 * (ver {@code data.sql}) para nunca ser materializada por engano.
 */
@Entity
@Table(name = "tb_asset_conteudo", uniqueConstraints = {
        @UniqueConstraint(name = "uk_asset_conteudo_hash", columnNames = {"hash"})
})
public class AssetConteudo {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "hash", nullable = false, length = 64)
    @Schema(description = "SHA-256 do arquivo em base64url, como o protocolo de updates espera")
    private String hash;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public AssetConteudo() {
    }

    public AssetConteudo(String hash, String contentType, long tamanhoBytes) {
        this.hash = hash;
        this.contentType = contentType;
        this.tamanhoBytes = tamanhoBytes;
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
    public String getHash() { return hash; }
    public String getContentType() { return contentType; }
    public long getTamanhoBytes() { return tamanhoBytes; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
