package com.gomesdev.sortifyteams.domain.versaoapp.conteudo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetConteudoRepository extends JpaRepository<AssetConteudo, String> {

    Optional<AssetConteudo> findByHash(String hash);
}
