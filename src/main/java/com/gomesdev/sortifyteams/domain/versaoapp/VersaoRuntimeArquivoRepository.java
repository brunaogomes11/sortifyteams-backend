package com.gomesdev.sortifyteams.domain.versaoapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VersaoRuntimeArquivoRepository extends JpaRepository<VersaoRuntimeArquivo, String> {

    Optional<VersaoRuntimeArquivo> findByVersaoRuntimeId(String versaoRuntimeId);

    boolean existsByVersaoRuntimeId(String versaoRuntimeId);

    void deleteByVersaoRuntimeId(String versaoRuntimeId);
}
