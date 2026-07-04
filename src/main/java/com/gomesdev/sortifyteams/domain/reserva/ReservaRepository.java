package com.gomesdev.sortifyteams.domain.reserva;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, String> {

    List<Reserva> findByRachaId(String rachaId);

    Optional<Reserva> findByRachaIdAndStatus(String rachaId, com.gomesdev.sortifyteams.enums.StatusReservaEnum status);
}
