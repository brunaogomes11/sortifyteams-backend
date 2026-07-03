package com.gomesdev.sortifyteams.domain.racha;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipanteRachaRepository extends JpaRepository<ParticipanteRacha, String> {

    List<ParticipanteRacha> findByRachaId(String rachaId);

    long countByRachaId(String rachaId);

    Optional<ParticipanteRacha> findByRachaIdAndUsuarioId(String rachaId, String usuarioId);

    boolean existsByRachaIdAndUsuarioId(String rachaId, String usuarioId);
}
