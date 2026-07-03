package com.gomesdev.sortifyteams.domain.notificacao.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, String> {

    List<PushToken> findByUsuarioId(String usuarioId);

    Optional<PushToken> findByUsuarioIdAndExpoToken(String usuarioId, String expoToken);
}
