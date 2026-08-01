package com.gomesdev.sortifyteams.domain.versaoapp;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Binário do APK (spec 002, C22) — expurgável pela C23.
 *
 * <p><b>A coluna {@code conteudo} não é mapeada aqui de propósito.</b> Se ela
 * fosse um campo da entidade, qualquer {@code findById} traria ~30 MB para a
 * heap, que é exatamente o que a FR-029 proíbe — e {@code @Basic(LAZY)} não
 * resolve sem bytecode enhancement. Por isso:
 *
 * <ul>
 *   <li>esta entidade cuida só dos metadados da linha;</li>
 *   <li>a coluna binária é criada no {@code data.sql}
 *       ({@code ADD COLUMN IF NOT EXISTS ... bytea}), fora do controle do
 *       Hibernate;</li>
 *   <li>gravação e leitura por faixa passam exclusivamente pelo
 *       {@link ApkBinarioRepository}, em SQL nativo.</li>
 * </ul>
 *
 * <p>Também é por isso que não se usa {@code @Lob}: em PostgreSQL o Hibernate
 * mapeia {@code @Lob byte[]} para {@code oid} (large object), que a decisão D2
 * descartou justamente por exigir desalocação explícita — e o expurgo da C23 é
 * uma rotina recorrente de apagar binário, ou seja, fabricaria órfãos.
 */
@Entity
@Table(name = "tb_versao_runtime_arquivo", uniqueConstraints = {
        @UniqueConstraint(name = "uk_versao_runtime_arquivo_versao",
                columnNames = {"versao_runtime_id"})
})
public class VersaoRuntimeArquivo {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    private String id;

    @Column(name = "versao_runtime_id", nullable = false, length = 26)
    private String versaoRuntimeId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public VersaoRuntimeArquivo() {
    }

    public VersaoRuntimeArquivo(String versaoRuntimeId) {
        this.versaoRuntimeId = versaoRuntimeId;
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
    public String getVersaoRuntimeId() { return versaoRuntimeId; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    public void setVersaoRuntimeId(String versaoRuntimeId) { this.versaoRuntimeId = versaoRuntimeId; }
}
