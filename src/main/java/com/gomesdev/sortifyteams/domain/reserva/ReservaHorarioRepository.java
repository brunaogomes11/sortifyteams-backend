package com.gomesdev.sortifyteams.domain.reserva;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReservaHorarioRepository extends JpaRepository<ReservaHorario, String> {

    List<ReservaHorario> findByQuadraHorarioIdInAndData(List<String> quadraHorarioIds, LocalDate data);

    List<ReservaHorario> findByReservaId(String reservaId);

    void deleteByReservaId(String reservaId);
}
