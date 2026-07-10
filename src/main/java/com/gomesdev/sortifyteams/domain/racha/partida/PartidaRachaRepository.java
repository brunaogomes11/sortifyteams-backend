package com.gomesdev.sortifyteams.domain.racha.partida;

import com.gomesdev.sortifyteams.enums.StatusPartidaEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartidaRachaRepository extends JpaRepository<PartidaRacha, String> {

    List<PartidaRacha> findByRachaIdOrderByIniciadaEmAsc(String rachaId);

    /** Deveria haver no máximo uma EM_ANDAMENTO por racha; a lista é defesa contra corrida. */
    List<PartidaRacha> findByRachaIdAndStatus(String rachaId, StatusPartidaEnum status);

    default Optional<PartidaRacha> findAtiva(String rachaId) {
        return findByRachaIdAndStatus(rachaId, StatusPartidaEnum.EM_ANDAMENTO).stream().findFirst();
    }
}
