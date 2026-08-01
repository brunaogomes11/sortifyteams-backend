package com.gomesdev.sortifyteams.domain.versaoapp.conteudo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

/**
 * Binário dos assets de conteúdo. Mesma abordagem do APK: coluna fora do
 * mapeamento JPA e acesso em SQL nativo.
 *
 * <p>Diferença de escala relevante: assets de conteúdo são pequenos (bundle de
 * poucos MB, imagens de KB), então aqui a leitura inteira é aceitável — não é
 * o caso de 30 MB do APK, que exige leitura por faixa.
 */
@Repository
public class AssetBinarioRepository {

    private final JdbcTemplate jdbcTemplate;

    public AssetBinarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void gravar(String assetId, byte[] conteudo) {
        jdbcTemplate.update("UPDATE tb_asset_conteudo SET conteudo = ? WHERE id = ?",
                conteudo, assetId);
    }

    public byte[] ler(String assetId) {
        return jdbcTemplate.queryForObject(
                "SELECT conteudo FROM tb_asset_conteudo WHERE id = ?", byte[].class, assetId);
    }

    public byte[] lerPorHash(String hash) {
        ResultSetExtractor<byte[]> primeiro = rs -> rs.next() ? rs.getBytes(1) : null;
        return jdbcTemplate.query(
                "SELECT conteudo FROM tb_asset_conteudo WHERE hash = ?", primeiro, hash);
    }

    /** Remove assets que não pertencem a nenhum pacote (expurgo da Fase 7). */
    public int apagarOrfaos() {
        return jdbcTemplate.update("""
                DELETE FROM tb_asset_conteudo a
                WHERE NOT EXISTS (SELECT 1 FROM tb_pacote_asset p WHERE p.asset_id = a.id)
                """);
    }
}
