package com.gomesdev.sortifyteams.domain.racha;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RachaRepository extends JpaRepository<Racha, String> {

    /** Rachas em que o usuário é organizador ou participante, mais recentes primeiro. */
    @Query("""
            select r from Racha r
            where r.organizadorId = :usuarioId
               or r.id in (select p.rachaId from ParticipanteRacha p where p.usuarioId = :usuarioId)
            order by r.criadoEm desc
            """)
    List<Racha> findDoUsuario(@Param("usuarioId") String usuarioId);

    Optional<Racha> findByTokenConvite(String tokenConvite);

    List<Racha> findByDataAndStatus(java.time.LocalDate data, com.gomesdev.sortifyteams.enums.StatusRachaEnum status);

    /** Contador de rachas concluídos do usuário (perfil — FR-012). */
    @Query("""
            select count(r) from Racha r
            where r.status = com.gomesdev.sortifyteams.enums.StatusRachaEnum.CONCLUIDO
              and (r.organizadorId = :usuarioId
                   or r.id in (select p.rachaId from ParticipanteRacha p where p.usuarioId = :usuarioId))
            """)
    long countConcluidosDoUsuario(@Param("usuarioId") String usuarioId);

    /** Esportes mais jogados pelo usuário em rachas concluídos (C11). */
    @Query("""
            select r.esporteId from Racha r
            where r.status = com.gomesdev.sortifyteams.enums.StatusRachaEnum.CONCLUIDO
              and (r.organizadorId = :usuarioId
                   or r.id in (select p.rachaId from ParticipanteRacha p where p.usuarioId = :usuarioId))
            group by r.esporteId
            order by count(r) desc
            """)
    List<String> esportesMaisJogados(@Param("usuarioId") String usuarioId);

    /** Dashboard admin (C14): rachas concluídos por quadra. */
    @Query("""
            select r.quadraId, count(r) from Racha r
            where r.status = com.gomesdev.sortifyteams.enums.StatusRachaEnum.CONCLUIDO
              and r.quadraId is not null
            group by r.quadraId
            order by count(r) desc
            """)
    List<Object[]> concluidosPorQuadra();
}
