package com.gomesdev.sortifyteams.domain.quadra;

import com.gomesdev.sortifyteams.config.storage.StorageService;
import com.gomesdev.sortifyteams.domain.quadra.request.HorarioRequest;
import com.gomesdev.sortifyteams.domain.quadra.request.HorariosRequest;
import com.gomesdev.sortifyteams.domain.quadra.request.QuadraRequest;
import com.gomesdev.sortifyteams.domain.quadra.response.FotoResponse;
import com.gomesdev.sortifyteams.domain.quadra.response.HorarioResponse;
import com.gomesdev.sortifyteams.domain.quadra.response.QuadraResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;

@Service
public class QuadraDonoService {

    private final QuadraRepository quadraRepository;
    private final QuadraFotoRepository fotoRepository;
    private final QuadraHorarioRepository horarioRepository;
    private final StorageService storageService;

    public QuadraDonoService(QuadraRepository quadraRepository,
                             QuadraFotoRepository fotoRepository,
                             QuadraHorarioRepository horarioRepository,
                             StorageService storageService) {
        this.quadraRepository = quadraRepository;
        this.fotoRepository = fotoRepository;
        this.horarioRepository = horarioRepository;
        this.storageService = storageService;
    }

    @Transactional
    public QuadraResponse criar(QuadraRequest request, Usuario dono) {
        Quadra quadra = quadraRepository.save(new Quadra(request, dono.getId()));
        return montarResponse(quadra);
    }

    @Transactional(readOnly = true)
    public List<QuadraResponse> listarDoDono(Usuario dono) {
        return quadraRepository.findByDonoIdOrderByCriadoEmDesc(dono.getId()).stream()
                .map(this::montarResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuadraResponse detalhar(String quadraId, Usuario dono) {
        return montarResponse(buscarDoDono(quadraId, dono));
    }

    @Transactional
    public QuadraResponse atualizar(String quadraId, QuadraRequest request, Usuario dono) {
        Quadra quadra = buscarDoDono(quadraId, dono);
        quadra.update(request);
        return montarResponse(quadraRepository.save(quadra));
    }

    @Transactional
    public void excluir(String quadraId, Usuario dono) {
        Quadra quadra = buscarDoDono(quadraId, dono);
        // Exclusão lógica: preserva histórico de reservas e some da busca.
        quadra.setAtiva(false);
        quadraRepository.save(quadra);
    }

    /** Substitui a grade semanal inteira, validando sobreposições (T027). */
    @Transactional
    public QuadraResponse definirHorarios(String quadraId, HorariosRequest request, Usuario dono) {
        Quadra quadra = buscarDoDono(quadraId, dono);
        validarGrade(request.horarios());

        horarioRepository.deleteByQuadraId(quadraId);
        request.horarios().forEach(h -> horarioRepository.save(new QuadraHorario(h, quadraId)));
        return montarResponse(quadra);
    }

    @Transactional
    public QuadraResponse adicionarFoto(String quadraId, MultipartFile arquivo, Usuario dono) {
        Quadra quadra = buscarDoDono(quadraId, dono);
        String contentType = arquivo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Envie um arquivo de imagem.");
        }
        try {
            String key = storageService.store(arquivo, "quadras/" + quadraId);
            int ordem = fotoRepository.findByQuadraIdOrderByOrdem(quadraId).size();
            fotoRepository.save(new QuadraFoto(quadraId, key, ordem));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return montarResponse(quadra);
    }

    @Transactional
    public QuadraResponse removerFoto(String quadraId, String fotoId, Usuario dono) {
        Quadra quadra = buscarDoDono(quadraId, dono);
        QuadraFoto foto = fotoRepository.findById(fotoId)
                .filter(f -> f.getQuadraId().equals(quadraId))
                .orElseThrow(() -> new EntityNotFoundException("Foto não encontrada nesta quadra."));
        fotoRepository.delete(foto);
        storageService.delete(foto.getPath());
        return montarResponse(quadra);
    }

    // ---------- privados ----------

    private Quadra buscarDoDono(String quadraId, Usuario dono) {
        Quadra quadra = quadraRepository.findById(quadraId)
                .orElseThrow(() -> new EntityNotFoundException("Quadra não encontrada: " + quadraId));
        if (!quadra.getDonoId().equals(dono.getId())) {
            throw new AccessDeniedException("Esta quadra pertence a outro dono.");
        }
        return quadra;
    }

    private void validarGrade(List<HorarioRequest> horarios) {
        for (HorarioRequest h : horarios) {
            if (!h.horaFim().isAfter(h.horaInicio())) {
                throw new IllegalArgumentException(
                        "Horário inválido: fim (%s) deve ser depois do início (%s)."
                                .formatted(h.horaFim(), h.horaInicio()));
            }
        }
        List<HorarioRequest> ordenados = horarios.stream()
                .sorted(Comparator.comparingInt(HorarioRequest::diaSemana)
                        .thenComparing(HorarioRequest::horaInicio))
                .toList();
        for (int i = 1; i < ordenados.size(); i++) {
            HorarioRequest anterior = ordenados.get(i - 1);
            HorarioRequest atual = ordenados.get(i);
            if (anterior.diaSemana().equals(atual.diaSemana())
                    && atual.horaInicio().isBefore(anterior.horaFim())) {
                throw new IllegalArgumentException(
                        "Horários sobrepostos no dia %d: %s–%s conflita com %s–%s."
                                .formatted(atual.diaSemana(), atual.horaInicio(), atual.horaFim(),
                                        anterior.horaInicio(), anterior.horaFim()));
            }
        }
    }

    private QuadraResponse montarResponse(Quadra quadra) {
        List<FotoResponse> fotos = fotoRepository.findByQuadraIdOrderByOrdem(quadra.getId()).stream()
                .map(f -> new FotoResponse(f.getId(), storageService.getUrl(f.getPath()), f.getOrdem()))
                .toList();
        List<HorarioResponse> horarios = horarioRepository
                .findByQuadraIdOrderByDiaSemanaAscHoraInicioAsc(quadra.getId()).stream()
                .map(HorarioResponse::new)
                .toList();
        return new QuadraResponse(quadra, fotos, horarios);
    }
}
