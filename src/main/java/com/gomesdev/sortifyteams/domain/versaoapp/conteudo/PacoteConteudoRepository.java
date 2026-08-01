package com.gomesdev.sortifyteams.domain.versaoapp.conteudo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PacoteConteudoRepository extends JpaRepository<PacoteConteudo, String> {

    Optional<PacoteConteudo> findByRuntimeVersionAndAtivoTrue(String runtimeVersion);

    List<PacoteConteudo> findAllByOrderByPublicadoEmDesc();

    /** Mantém a invariante de um só pacote ativo por runtime. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PacoteConteudo p set p.ativo = false "
            + "where p.runtimeVersion = :runtime and p.ativo = true")
    int desativarDoRuntime(@Param("runtime") String runtimeVersion);
}
