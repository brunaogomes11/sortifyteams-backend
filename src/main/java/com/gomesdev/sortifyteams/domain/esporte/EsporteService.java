package com.gomesdev.sortifyteams.domain.esporte;

import com.gomesdev.sortifyteams.domain.esporte.response.EsporteResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EsporteService {

    private final EsporteRepository repository;

    public EsporteService(EsporteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EsporteResponse> listar() {
        return repository.findAll(Sort.by("nome")).stream()
                .map(EsporteResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public Esporte buscarEntidade(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Esporte não encontrado: " + id));
    }
}
