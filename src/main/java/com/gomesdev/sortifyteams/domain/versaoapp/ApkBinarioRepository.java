package com.gomesdev.sortifyteams.domain.versaoapp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;

/**
 * Acesso ao binário do APK em SQL nativo (spec 002, D2/FR-029).
 *
 * <p>Existe separado do repositório JPA porque as duas operações que importam
 * aqui — gravar por stream e ler uma faixa de bytes — não são expressáveis em
 * JPA sem materializar o arquivo inteiro na heap:
 *
 * <ul>
 *   <li><b>gravar</b>: {@code setBinaryStream} entrega o upload ao driver do
 *       Postgres em fluxo, sem {@code MultipartFile.getBytes()};</li>
 *   <li><b>ler faixa</b> (Fase 1): {@code substring(conteudo from ? for ?)}
 *       devolve só o pedaço pedido — e só é eficiente porque a coluna está com
 *       {@code STORAGE EXTERNAL} (sem compressão), aplicado no {@code data.sql}.
 *       Com o padrão {@code EXTENDED}, o Postgres precisaria descomprimir o
 *       valor inteiro para devolver qualquer fatia.</li>
 * </ul>
 */
@Repository
public class ApkBinarioRepository {

    private final JdbcTemplate jdbcTemplate;

    public ApkBinarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Grava o binário associado a uma versão, lendo do stream informado.
     * O chamador é responsável pela transação — a publicação (C22) exige que
     * metadados e binário vivam ou morram juntos.
     *
     * <p><b>É um UPDATE, não INSERT.</b> A linha de {@code tb_versao_runtime_arquivo}
     * já existe quando isto roda — {@code VersaoRuntimeArquivoRepository.save()}
     * (JPA) já a criou com {@code conteudo} nulo, porque a coluna binária fica
     * fora do mapeamento. Inserir aqui de novo colidiria com a constraint única
     * em {@code versao_runtime_id} (bug real, pego só contra Postgres — o teste
     * unitário mockava este repositório e não via a colisão).
     */
    public void gravar(String versaoRuntimeId, InputStream conteudo, long tamanhoBytes) {
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            String sql = "UPDATE tb_versao_runtime_arquivo SET conteudo = ? WHERE versao_runtime_id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setBinaryStream(1, conteudo, tamanhoBytes);
                ps.setString(2, versaoRuntimeId);
                int atualizadas = ps.executeUpdate();
                if (atualizadas == 0) {
                    throw new IllegalStateException(
                            "Nenhuma linha de tb_versao_runtime_arquivo para versao_runtime_id=%s — "
                                    .formatted(versaoRuntimeId)
                                    + "a linha de metadados precisa existir antes de gravar o binário.");
                }
            } finally {
                fecharSilenciosamente(conteudo);
            }
            return null;
        });
    }

    /**
     * Lê uma faixa do binário sem materializar o arquivo inteiro (FR-010/FR-029).
     *
     * <p><b>Atenção ao índice</b>: {@code offset} é 0-based, como o
     * {@code Range} do HTTP; o {@code substr} do SQL é 1-based. O {@code +1}
     * abaixo é a conversão — errar por 1 byte aqui gera um APK que baixa
     * inteiro e só falha na verificação de assinatura do Android, que é o
     * sintoma mais difícil de diagnosticar da feature.
     *
     * <p>Usa-se {@code substr(...)} em vez da forma SQL
     * {@code substring(x from ? for ?)} porque a primeira aceita parâmetros
     * JDBC sem ambiguidade de parsing.
     *
     * <p><b>O cast para {@code int} é obrigatório, não estético.</b>
     * {@code substr(bytea, integer, integer)} não tem overload para
     * {@code bigint}; passar {@code offset + 1} como {@code long} faz o
     * Spring inferir {@code bigint} no bind, e o Postgres recusa a função
     * inteira com "function substr(bytea, bigint, integer) does not exist"
     * (SQLState 42883 → {@code BadSqlGrammarException}). Seguro até ~2 GB de
     * arquivo, muito acima do tamanho de qualquer APK.
     */
    public byte[] lerFaixa(String versaoRuntimeId, long offset, int tamanho) {
        return jdbcTemplate.queryForObject(
                "SELECT substr(conteudo, ?, ?) FROM tb_versao_runtime_arquivo WHERE versao_runtime_id = ?",
                byte[].class,
                (int) (offset + 1), tamanho, versaoRuntimeId);
    }

    /** Remove o binário preservando os metadados da versão (expurgo da C23). */
    public int apagarBinario(String versaoRuntimeId) {
        return jdbcTemplate.update(
                "DELETE FROM tb_versao_runtime_arquivo WHERE versao_runtime_id = ?", versaoRuntimeId);
    }

    /** Tamanho real gravado no banco — usado para conferir contra os metadados. */
    public Long tamanhoGravado(String versaoRuntimeId) {
        return jdbcTemplate.query(
                "SELECT octet_length(conteudo) FROM tb_versao_runtime_arquivo WHERE versao_runtime_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                versaoRuntimeId);
    }

    public boolean existeBinario(String versaoRuntimeId) {
        // O extractor precisa ser tipado: um lambda solto aqui fica ambíguo
        // entre ResultSetExtractor e RowCallbackHandler.
        ResultSetExtractor<Boolean> temLinha = rs -> rs.next();
        Boolean existe = jdbcTemplate.query(
                "SELECT 1 FROM tb_versao_runtime_arquivo WHERE versao_runtime_id = ?",
                temLinha,
                versaoRuntimeId);
        return Boolean.TRUE.equals(existe);
    }

    private void fecharSilenciosamente(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // o stream do multipart já pode ter sido fechado pelo container
        }
    }
}
