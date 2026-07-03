package com.gomesdev.sortifyteams.domain.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, String> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<Usuario> findTop20ByUsernameContainingIgnoreCaseOrNomeCompletoContainingIgnoreCase(
            String username, String nomeCompleto);
}
