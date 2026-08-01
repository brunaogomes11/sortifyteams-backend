package com.gomesdev.sortifyteams.domain.versaoapp.conteudo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PacoteAssetRepository extends JpaRepository<PacoteAsset, String> {

    List<PacoteAsset> findByPacoteId(String pacoteId);
}
