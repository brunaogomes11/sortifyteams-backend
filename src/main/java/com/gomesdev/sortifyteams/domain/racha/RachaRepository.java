package com.gomesdev.sortifyteams.domain.racha;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RachaRepository extends JpaRepository<Racha, String> {

    /** Rachas em que o usuário é organizador ou participante, mais recentes primeiro. */
    @Query("""
            select r from Racha r
            where r.organizadorId = :usuarioId
               or r.id in (select p.rachaId from ParticipanteRacha p where p.usuarioId = :usuarioId)
            order by r.criadoEm desc
            """)
    List<Racha> findDoUsuario(@Param("usuarioId") String usuarioId);
}
