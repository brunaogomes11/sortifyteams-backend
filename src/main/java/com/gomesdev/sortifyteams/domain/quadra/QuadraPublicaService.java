package com.gomesdev.sortifyteams.domain.quadra;

import com.gomesdev.sortifyteams.config.storage.StorageService;
import com.gomesdev.sortifyteams.domain.quadra.response.HorarioResponse;
import com.gomesdev.sortifyteams.domain.quadra.response.QuadraPublicaResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Visão pública das quadras para organizadores (Fluxo 4). */
@Service
public class QuadraPublicaService {

    private final QuadraRepository quadraRepository;
    private final QuadraFotoRepository fotoRepository;
    private final QuadraHorarioRepository horarioRepository;
    private final StorageService storageService;

    public QuadraPublicaService(QuadraRepository quadraRepository,
                                QuadraFotoRepository fotoRepository,
                                QuadraHorarioRepository horarioRepository,
                                StorageService storageService) {
        this.quadraRepository = quadraRepository;
        this.fotoRepository = fotoRepository;
        this.horarioRepository = horarioRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public Page<QuadraPublicaResponse> listar(String busca, Pageable pageable) {
        String termo = busca == null ? "" : busca.trim();
        return quadraRepository.buscarAtivas(termo, pageable).map(this::montarResponse);
    }

    @Transactional(readOnly = true)
    public QuadraPublicaResponse detalhar(String quadraId) {
        return montarResponse(buscarAtiva(quadraId));
    }

    @Transactional(readOnly = true)
    public Quadra buscarAtiva(String quadraId) {
        return quadraRepository.findById(quadraId)
                .filter(Quadra::isAtiva)
                .orElseThrow(() -> new EntityNotFoundException("Quadra não encontrada: " + quadraId));
    }

    private QuadraPublicaResponse montarResponse(Quadra quadra) {
        List<String> fotos = fotoRepository.findByQuadraIdOrderByOrdem(quadra.getId()).stream()
                .map(f -> storageService.getUrl(f.getPath()))
                .toList();
        List<HorarioResponse> horarios = horarioRepository
                .findByQuadraIdOrderByDiaSemanaAscHoraInicioAsc(quadra.getId()).stream()
                .map(HorarioResponse::new)
                .toList();
        return new QuadraPublicaResponse(quadra, fotos, horarios);
    }
}
