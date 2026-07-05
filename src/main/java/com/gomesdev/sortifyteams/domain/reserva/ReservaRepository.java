package com.gomesdev.sortifyteams.domain.reserva;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, String> {

    List<Reserva> findByRachaId(String rachaId);

    Optional<Reserva> findByRachaIdAndStatus(String rachaId, com.gomesdev.sortifyteams.enums.StatusReservaEnum status);

    /** Agenda do dono (T034): reservas nas quadras dele no período. */
    @Query("""
            select r from Reserva r
            where r.quadraId in (select q.id from Quadra q where q.donoId = :donoId)
              and r.data between :de and :ate
            order by r.data, r.criadoEm
            """)
    List<Reserva> agendaDoDono(@Param("donoId") String donoId,
                               @Param("de") LocalDate de,
                               @Param("ate") LocalDate ate);
}
