package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VersaoRuntimeRepository extends JpaRepository<VersaoRuntime, String> {

    Optional<VersaoRuntime> findByPlataformaAndAtivaTrue(PlataformaAppEnum plataforma);

    Optional<VersaoRuntime> findByPlataformaAndVersionCode(PlataformaAppEnum plataforma, int versionCode);

    List<VersaoRuntime> findByPlataformaOrderByVersionCodeDesc(PlataformaAppEnum plataforma);

    @Query("select max(v.versionCode) from VersaoRuntime v where v.plataforma = :plataforma")
    Optional<Integer> maiorVersionCode(@Param("plataforma") PlataformaAppEnum plataforma);

    /**
     * Desativa todas as versões da plataforma. Chamado dentro da transação de
     * publicação/ativação para manter a invariante de <b>uma só ativa</b>.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update VersaoRuntime v set v.ativa = false where v.plataforma = :plataforma and v.ativa = true")
    int desativarTodas(@Param("plataforma") PlataformaAppEnum plataforma);
}
