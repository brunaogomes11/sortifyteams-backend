package com.gomesdev.sortifyteams.domain.notificacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, String> {

    List<Notificacao> findTop50ByUsuarioIdOrderByCriadaEmDesc(String usuarioId);

    long countByUsuarioIdAndLidaFalse(String usuarioId);
}
