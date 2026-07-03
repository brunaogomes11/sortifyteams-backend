package com.gomesdev.sortifyteams.domain.quadra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuadraFotoRepository extends JpaRepository<QuadraFoto, String> {

    List<QuadraFoto> findByQuadraIdOrderByOrdem(String quadraId);
}
