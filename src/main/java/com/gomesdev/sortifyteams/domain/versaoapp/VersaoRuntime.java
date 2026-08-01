package com.gomesdev.sortifyteams.domain.versaoapp;

import com.github.f4b6a3.ulid.UlidCreator;
import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Metadados de uma versão de runtime do app (spec 002, C22) — o registro é
 * <b>permanente</b>. O binário fica em {@link VersaoRuntimeArquivo} e é o
 * único que o expurgo da C23 remove; apagar o arquivo nunca apaga esta linha,
 * por isso {@code sha256}, {@code md5} e {@code tamanhoBytes} continuam
 * preenchidos depois do expurgo — são o registro do que a versão era.
 */
@Entity
@Table(name = "tb_versao_runtime", uniqueConstraints = {
        @UniqueConstraint(name = "uk_versao_runtime_plataforma_code",
                columnNames = {"plataforma", "version_code"})
}, indexes = {
        @Index(name = "idx_versao_runtime_ativa", columnList = "plataforma, ativa")
})
public class VersaoRuntime {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "plataforma", nullable = false, length = 20)
    private PlataformaAppEnum plataforma = PlataformaAppEnum.ANDROID;

    @Column(name = "versao", nullable = false, length = 40)
    @Schema(description = "Versão semver, usada só para exibição (ex.: 1.2.0)")
    private String versao;

    @Column(name = "version_code", nullable = false)
    @Schema(description = "Inteiro crescente do Android — é o que o instalador respeita (C3)")
    private int versionCode;

    @Column(name = "runtime_version", nullable = false, length = 40)
    @Schema(description = "Amarra a camada de conteúdo (C13): OTA só se aplica a runtime igual")
    private String runtimeVersion;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    @Schema(description = "ETag do download e registro permanente da versão (D3/D4)")
    private String sha256;

    @Column(name = "md5", nullable = false, length = 32)
    @Schema(description = "Conferido no dispositivo após o download (D4)")
    private String md5;

    @Column(name = "notas", columnDefinition = "text")
    private String notas;

    @Column(name = "version_code_minimo", nullable = false)
    @Schema(description = "Abaixo disto a atualização é obrigatória e bloqueante (C4)")
    private int versionCodeMinimo;

    @Column(name = "ativa", nullable = false)
    @Schema(description = "No máximo uma ativa por plataforma — invariante garantida em transação")
    private boolean ativa;

    @Column(name = "publicada_em", nullable = false)
    private LocalDateTime publicadaEm;

    @Column(name = "publicada_por_id", length = 26)
    private String publicadaPorId;

    @Column(name = "binario_expurgado_em")
    @Schema(description = "Preenchido pelo expurgo da C23; a partir daí a versão é só histórico")
    private LocalDateTime binarioExpurgadoEm;

    public VersaoRuntime() {
    }

    public VersaoRuntime(PlataformaAppEnum plataforma, String versao, int versionCode,
                         String runtimeVersion, String notas, int versionCodeMinimo,
                         String publicadaPorId) {
        this.plataforma = plataforma;
        this.versao = versao;
        this.versionCode = versionCode;
        this.runtimeVersion = runtimeVersion;
        this.notas = notas;
        this.versionCodeMinimo = versionCodeMinimo;
        this.publicadaPorId = publicadaPorId;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
        if (this.publicadaEm == null) {
            this.publicadaEm = LocalDateTime.now();
        }
    }

    /** Versão que já perdeu o binário para o expurgo (C23) — só histórico. */
    public boolean isSomenteHistorico() {
        return binarioExpurgadoEm != null;
    }

    public String getId() { return id; }
    public PlataformaAppEnum getPlataforma() { return plataforma; }
    public String getVersao() { return versao; }
    public int getVersionCode() { return versionCode; }
    public String getRuntimeVersion() { return runtimeVersion; }
    public long getTamanhoBytes() { return tamanhoBytes; }
    public String getSha256() { return sha256; }
    public String getMd5() { return md5; }
    public String getNotas() { return notas; }
    public int getVersionCodeMinimo() { return versionCodeMinimo; }
    public boolean isAtiva() { return ativa; }
    public LocalDateTime getPublicadaEm() { return publicadaEm; }
    public String getPublicadaPorId() { return publicadaPorId; }
    public LocalDateTime getBinarioExpurgadoEm() { return binarioExpurgadoEm; }

    public void setPlataforma(PlataformaAppEnum plataforma) { this.plataforma = plataforma; }
    public void setVersao(String versao) { this.versao = versao; }
    public void setVersionCode(int versionCode) { this.versionCode = versionCode; }
    public void setRuntimeVersion(String runtimeVersion) { this.runtimeVersion = runtimeVersion; }
    public void setTamanhoBytes(long tamanhoBytes) { this.tamanhoBytes = tamanhoBytes; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public void setMd5(String md5) { this.md5 = md5; }
    public void setNotas(String notas) { this.notas = notas; }
    public void setVersionCodeMinimo(int versionCodeMinimo) { this.versionCodeMinimo = versionCodeMinimo; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
    public void setPublicadaEm(LocalDateTime publicadaEm) { this.publicadaEm = publicadaEm; }
    public void setPublicadaPorId(String publicadaPorId) { this.publicadaPorId = publicadaPorId; }
    public void setBinarioExpurgadoEm(LocalDateTime binarioExpurgadoEm) {
        this.binarioExpurgadoEm = binarioExpurgadoEm;
    }
}
