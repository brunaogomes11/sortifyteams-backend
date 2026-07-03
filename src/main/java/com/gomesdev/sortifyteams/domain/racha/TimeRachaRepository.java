package com.gomesdev.sortifyteams.domain.racha;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeRachaRepository extends JpaRepository<TimeRacha, String> {

    List<TimeRacha> findByRachaIdOrderByNumero(String rachaId);

    void deleteByRachaId(String rachaId);
}
