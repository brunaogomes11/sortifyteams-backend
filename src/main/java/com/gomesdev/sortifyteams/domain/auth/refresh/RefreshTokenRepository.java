package com.gomesdev.sortifyteams.domain.auth.refresh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revogado = true where t.usuarioId = :usuarioId and t.revogado = false")
    void revogarTodosDoUsuario(@Param("usuarioId") String usuarioId);
}
