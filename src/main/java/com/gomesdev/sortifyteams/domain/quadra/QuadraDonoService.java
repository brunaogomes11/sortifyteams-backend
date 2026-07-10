package com.gomesdev.sortifyteams.domain.quadra;

import com.gomesdev.sortifyteams.config.geo.GeocodingService;
import com.gomesdev.sortifyteams.config.storage.StorageService;
import com.gomesdev.sortifyteams.domain.quadra.request.HorarioRequest;
import com.gomesdev.sortifyteams.domain.quadra.request.HorariosRequest;
import com.gomesdev.sortifyteams.domain.quadra.request.QuadraRequest;
import com.gomesdev.sortifyteams.domain.quadra.response.FotoResponse;
import com.gomesdev.sortifyteams.domain.quadra.response.HorarioResponse;
import com.gomesdev.sortifyteams.domain.quadra.response.QuadraResponse;
import com.gomesdev.sortifyteams.domain.reserva.ReservaRepository;
import com.gomesdev.sortifyteams.domain.reserva.ReservaService;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.enums.StatusReservaEnum;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class QuadraDonoService {

    private final QuadraRepository quadraRepository;
    private final QuadraFotoRepository fotoRepository;
    private final QuadraHorarioRepository horarioRepository;
    private final StorageService storageService;
    private final GeocodingService geocodingService;
    private final ReservaRepository reservaRepository;
    private final ReservaService reservaService;

    public QuadraDonoService(QuadraRepository quadraRepository,
                             QuadraFotoRepository fotoRepository,
                             QuadraHorarioRepository horarioRepository,
                             StorageService storageService,
                             GeocodingService geocodingService,
                             ReservaRepository reservaRepository,
                             ReservaService reservaService) {
        this.quadraRepository = quadraRepository;
        this.fotoRepository = fotoRepository;
        this.horarioRepository = horarioRepository;
        this.storageService = storageService;
        this.geocodingService = geocodingService;
        this.reservaRepository = reservaRepository;
        this.reservaService = reservaService;
    }

    @Transactional
    public QuadraResponse criar(QuadraRequest request, Usuario dono) {
        Quadra quadra = new Quadra(request, dono.getId());
        geocodificar(quadra);
        return montarResponse(quadraRepository.save(quadra));
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
        String enderecoAnterior = quadra.getEndereco();
        quadra.update(request);
        // Só bate no Nominatim quando o endereço mudou (ou nunca foi resolvido).
        if (!request.endereco().equals(enderecoAnterior) || quadra.getLatitude() == null) {
            geocodificar(quadra);
        }
        return montarResponse(quadraRepository.save(quadra));
    }

    @Transactional
    public void excluir(String quadraId, Usuario dono) {
        Quadra quadra = buscarDoDono(quadraId, dono);
        // Exclusão lógica: preserva histórico de reservas e some da busca.
        quadra.setAtiva(false);
        quadraRepository.save(quadra);
        // Reservas futuras não podem ficar presas numa quadra invisível — cancela
        // em cascata e notifica organizadores e jogadores (FIX 3).
        reservaService.cancelarPorDesativacaoDaQuadra(quadraId);
    }

    /**
     * Substitui a grade semanal inteira, validando sobreposições (T027).
     * Cada item do request é uma faixa ("das 18h às 23h") expandida em slots
     * de 1 hora reserváveis separadamente, com o preço informado por hora (FIX 15).
     */
    @Transactional
    public QuadraResponse definirHorarios(String quadraId, HorariosRequest request, Usuario dono) {
        Quadra quadra = buscarDoDono(quadraId, dono);
        // Apagar slots referenciados por reservas confirmadas futuras quebraria a
        // reserva do organizador e liberaria overbooking do horário real (FIX 2).
        if (reservaRepository.existsByQuadraIdAndStatusInAndDataGreaterThanEqual(
                quadraId, List.of(StatusReservaEnum.PENDENTE, StatusReservaEnum.CONFIRMADA), LocalDate.now())) {
            throw new IllegalArgumentException(
                    "A quadra tem reservas ativas a partir de hoje. Cancele-as antes de alterar a grade de horários.");
        }
        validarGrade(request.horarios());

        horarioRepository.deleteByQuadraId(quadraId);
        request.horarios().forEach(faixa -> expandirEmSlotsDeUmaHora(faixa, quadraId)
                .forEach(horarioRepository::save));
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

    /** Resolve lat/long/cidade do endereço (best-effort). Sem resultado, zera as coordenadas. */
    private void geocodificar(Quadra quadra) {
        geocodingService.geocodificar(quadra.getEndereco()).ifPresentOrElse(r -> {
            quadra.setLatitude(r.latitude());
            quadra.setLongitude(r.longitude());
            quadra.setCidade(r.cidade());
        }, () -> {
            quadra.setLatitude(null);
            quadra.setLongitude(null);
            quadra.setCidade(null);
        });
    }

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
            if (Duration.between(h.horaInicio(), h.horaFim()).toMinutes() % 60 != 0) {
                throw new IllegalArgumentException(
                        "A faixa %s–%s deve fechar horas inteiras — cada reserva dura 1 hora."
                                .formatted(h.horaInicio(), h.horaFim()));
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

    /** "18:00–21:00" vira três slots reserváveis: 18–19, 19–20 e 20–21, cada um com o preço da hora. */
    private List<QuadraHorario> expandirEmSlotsDeUmaHora(HorarioRequest faixa, String quadraId) {
        List<QuadraHorario> slots = new ArrayList<>();
        for (LocalTime inicio = faixa.horaInicio(); inicio.isBefore(faixa.horaFim()); inicio = inicio.plusHours(1)) {
            slots.add(new QuadraHorario(quadraId, faixa.diaSemana(), inicio, inicio.plusHours(1), faixa.preco()));
        }
        return slots;
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
