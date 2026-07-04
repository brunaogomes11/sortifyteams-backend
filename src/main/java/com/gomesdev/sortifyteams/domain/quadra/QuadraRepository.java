package com.gomesdev.sortifyteams.domain.quadra;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuadraRepository extends JpaRepository<Quadra, String> {

    List<Quadra> findByDonoIdOrderByCriadoEmDesc(String donoId);

    /** Busca pública de quadras ativas por nome ou endereço (Fluxo 4.1). */
    @Query("""
            select q from Quadra q
            where q.ativa = true
              and (lower(q.nome) like lower(concat('%', :busca, '%'))
                   or lower(q.endereco) like lower(concat('%', :busca, '%')))
            order by q.nome
            """)
    Page<Quadra> buscarAtivas(@Param("busca") String busca, Pageable pageable);
}
