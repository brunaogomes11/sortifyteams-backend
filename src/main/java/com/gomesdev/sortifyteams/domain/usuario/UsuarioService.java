package com.gomesdev.sortifyteams.domain.usuario;

import com.gomesdev.sortifyteams.domain.usuario.response.UsuarioBuscaResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository repository;

    public UsuarioService(UsuarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + username));
    }

    /** Busca usuários para convite em racha (C3). Retorna só o mínimo (FR-016). */
    @Transactional(readOnly = true)
    public List<UsuarioBuscaResponse> buscar(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return repository
                .findTop20ByUsernameContainingIgnoreCaseOrNomeCompletoContainingIgnoreCase(query, query)
                .stream()
                .map(UsuarioBuscaResponse::new)
                .toList();
    }
}
