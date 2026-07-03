package com.gomesdev.sortifyteams.domain.quadra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuadraHorarioRepository extends JpaRepository<QuadraHorario, String> {

    List<QuadraHorario> findByQuadraIdOrderByDiaSemanaAscHoraInicioAsc(String quadraId);

    void deleteByQuadraId(String quadraId);
}
