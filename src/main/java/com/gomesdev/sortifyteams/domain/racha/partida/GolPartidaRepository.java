package com.gomesdev.sortifyteams.domain.racha.partida;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GolPartidaRepository extends JpaRepository<GolPartida, String> {

    List<GolPartida> findByRachaIdOrderByCriadoEmAsc(String rachaId);
}
