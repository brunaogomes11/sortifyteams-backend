package com.gomesdev.sortifyteams.domain.quadra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuadraRepository extends JpaRepository<Quadra, String> {

    List<Quadra> findByDonoIdOrderByCriadoEmDesc(String donoId);
}
