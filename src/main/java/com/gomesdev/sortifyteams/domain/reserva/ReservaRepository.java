package com.gomesdev.sortifyteams.domain.reserva;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gomesdev.sortifyteams.enums.StatusReservaEnum;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, String> {

    List<Reserva> findByRachaId(String rachaId);

    Optional<Reserva> findByRachaIdAndStatus(String rachaId, StatusReservaEnum status);

    /** Um racha só pode ter uma reserva ativa por vez (pendente ou confirmada). */
    boolean existsByRachaIdAndStatusIn(String rachaId, Collection<StatusReservaEnum> status);

    /** Reservas ativas (pendentes/confirmadas) de um racha — canceladas em cascata (C10). */
    List<Reserva> findByRachaIdAndStatusIn(String rachaId, Collection<StatusReservaEnum> status);

    /** Bloqueia a redefinição da grade enquanto houver reserva ativa futura (FIX 2). */
    boolean existsByQuadraIdAndStatusInAndDataGreaterThanEqual(
            String quadraId, Collection<StatusReservaEnum> status, LocalDate data);

    /** Reservas ativas futuras de uma quadra — canceladas em cascata ao desativá-la (FIX 3). */
    List<Reserva> findByQuadraIdAndStatusInAndDataGreaterThanEqual(
            String quadraId, Collection<StatusReservaEnum> status, LocalDate data);

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
