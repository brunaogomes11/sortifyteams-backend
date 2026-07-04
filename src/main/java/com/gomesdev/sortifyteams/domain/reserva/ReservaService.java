package com.gomesdev.sortifyteams.domain.reserva;

import com.gomesdev.sortifyteams.domain.notificacao.NotificacaoService;
import com.gomesdev.sortifyteams.domain.quadra.Quadra;
import com.gomesdev.sortifyteams.domain.quadra.QuadraHorario;
import com.gomesdev.sortifyteams.domain.quadra.QuadraHorarioRepository;
import com.gomesdev.sortifyteams.domain.quadra.QuadraPublicaService;
import com.gomesdev.sortifyteams.domain.quadra.response.HorarioResponse;
import com.gomesdev.sortifyteams.domain.racha.Racha;
import com.gomesdev.sortifyteams.domain.racha.RachaRepository;
import com.gomesdev.sortifyteams.domain.reserva.request.ReservaRequest;
import com.gomesdev.sortifyteams.domain.reserva.response.DisponibilidadeResponse;
import com.gomesdev.sortifyteams.domain.reserva.response.ReservaResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import com.gomesdev.sortifyteams.enums.StatusReservaEnum;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Reserva de quadra (Fluxo 4) — regra crítica de conflito C8/FR-009. */
@Service
public class ReservaService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ReservaRepository reservaRepository;
    private final ReservaHorarioRepository reservaHorarioRepository;
    private final QuadraHorarioRepository quadraHorarioRepository;
    private final QuadraPublicaService quadraPublicaService;
    private final RachaRepository rachaRepository;
    private final NotificacaoService notificacaoService;

    public ReservaService(ReservaRepository reservaRepository,
                          ReservaHorarioRepository reservaHorarioRepository,
                          QuadraHorarioRepository quadraHorarioRepository,
                          QuadraPublicaService quadraPublicaService,
                          RachaRepository rachaRepository,
                          NotificacaoService notificacaoService) {
        this.reservaRepository = reservaRepository;
        this.reservaHorarioRepository = reservaHorarioRepository;
        this.quadraHorarioRepository = quadraHorarioRepository;
        this.quadraPublicaService = quadraPublicaService;
        this.rachaRepository = rachaRepository;
        this.notificacaoService = notificacaoService;
    }

    @Transactional
    public ReservaResponse criar(ReservaRequest request, Usuario organizador) {
        Racha racha = rachaRepository.findById(request.rachaId())
                .orElseThrow(() -> new EntityNotFoundException("Racha não encontrado: " + request.rachaId()));
        if (!racha.getOrganizadorId().equals(organizador.getId())) {
            throw new AccessDeniedException("Apenas o organizador do racha pode reservar.");
        }
        if (racha.getStatus() != StatusRachaEnum.ABERTO) {
            throw new IllegalArgumentException("O racha não está mais aberto.");
        }
        if (reservaRepository.findByRachaIdAndStatus(racha.getId(), StatusReservaEnum.CONFIRMADA).isPresent()) {
            throw new IllegalArgumentException("Este racha já tem uma reserva confirmada.");
        }

        Quadra quadra = quadraPublicaService.buscarAtiva(request.quadraId());

        Set<String> idsUnicos = new HashSet<>(request.quadraHorarioIds());
        List<QuadraHorario> slots = quadraHorarioRepository.findAllById(idsUnicos);
        if (slots.size() != idsUnicos.size()) {
            throw new EntityNotFoundException("Um ou mais horários não existem.");
        }
        int diaSemana = request.data().getDayOfWeek().getValue() % 7; // 0=domingo ... 6=sábado
        for (QuadraHorario slot : slots) {
            if (!slot.getQuadraId().equals(quadra.getId())) {
                throw new IllegalArgumentException("Horário não pertence a esta quadra.");
            }
            if (slot.getDiaSemana() != diaSemana) {
                throw new IllegalArgumentException(
                        "O horário %s–%s é de outro dia da semana (a data %s cai em %s)."
                                .formatted(slot.getHoraInicio().format(HORA), slot.getHoraFim().format(HORA),
                                        request.data(), request.data().getDayOfWeek()));
            }
        }

        // Pré-checagem amigável; a UNIQUE (quadra_horario_id, data) é o guarda-costas
        // contra corrida entre transações (C8).
        List<ReservaHorario> ocupados = reservaHorarioRepository
                .findByQuadraHorarioIdInAndData(List.copyOf(idsUnicos), request.data());
        if (!ocupados.isEmpty()) {
            throw conflito(quadra.getId(), request.data());
        }

        BigDecimal precoTotal = slots.stream()
                .map(QuadraHorario::getPreco)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Se outro organizador confirmar o mesmo slot entre a checagem acima e o
        // commit, a UNIQUE estoura DataIntegrityViolationException — convertida em
        // 409 com alternativas no controller (a transação abortada não permite
        // consultar as alternativas aqui dentro).
        Reserva reserva = reservaRepository.save(new Reserva(quadra.getId(), racha.getId(), request.data(), precoTotal));
        slots.forEach(slot -> reservaHorarioRepository.save(
                new ReservaHorario(reserva.getId(), slot.getId(), request.data())));
        reservaHorarioRepository.flush();

        racha.setQuadraId(quadra.getId());
        racha.setData(request.data());
        racha.setHorario(slots.stream().map(QuadraHorario::getHoraInicio).min(java.time.LocalTime::compareTo).orElse(null));
        rachaRepository.save(racha);

        notificacaoService.notificar(quadra.getDonoId(), "RESERVA_CRIADA",
                "Nova reserva na " + quadra.getNome(),
                "Reserva confirmada para %s (%s horário%s)."
                        .formatted(request.data(), slots.size(), slots.size() == 1 ? "" : "s"));

        return montarResponse(reserva, quadra);
    }

    @Transactional(readOnly = true)
    public ReservaResponse detalhar(String reservaId, Usuario usuario) {
        Reserva reserva = buscarEntidade(reservaId);
        Quadra quadra = quadraPublicaService.buscarAtiva(reserva.getQuadraId());
        Racha racha = rachaRepository.findById(reserva.getRachaId()).orElseThrow();
        boolean organizador = racha.getOrganizadorId().equals(usuario.getId());
        boolean dono = quadra.getDonoId().equals(usuario.getId());
        if (!organizador && !dono) {
            throw new AccessDeniedException("Você não participa desta reserva.");
        }
        return montarResponse(reserva, quadra);
    }

    /** Cancelamento pelo organizador: libera os slots e avisa o dono (C10). */
    @Transactional
    public void cancelar(String reservaId, Usuario usuario) {
        Reserva reserva = buscarEntidade(reservaId);
        Racha racha = rachaRepository.findById(reserva.getRachaId()).orElseThrow();
        if (!racha.getOrganizadorId().equals(usuario.getId())) {
            throw new AccessDeniedException("Apenas o organizador pode cancelar a reserva.");
        }
        if (reserva.getStatus() != StatusReservaEnum.CONFIRMADA) {
            throw new IllegalArgumentException("A reserva já está cancelada.");
        }

        reserva.setStatus(StatusReservaEnum.CANCELADA_ORGANIZADOR);
        reservaRepository.save(reserva);
        reservaHorarioRepository.deleteByReservaId(reserva.getId());

        racha.setQuadraId(null);
        rachaRepository.save(racha);

        Quadra quadra = quadraPublicaService.buscarAtiva(reserva.getQuadraId());
        notificacaoService.notificar(quadra.getDonoId(), "RESERVA_CANCELADA",
                "Reserva cancelada na " + quadra.getNome(),
                "O organizador cancelou a reserva de %s.".formatted(reserva.getData()));
    }

    /** Grade do dia da semana da data, marcando o que já está reservado. */
    @Transactional(readOnly = true)
    public DisponibilidadeResponse disponibilidade(String quadraId, LocalDate data) {
        quadraPublicaService.buscarAtiva(quadraId);
        int diaSemana = data.getDayOfWeek().getValue() % 7;
        List<QuadraHorario> grade = quadraHorarioRepository
                .findByQuadraIdOrderByDiaSemanaAscHoraInicioAsc(quadraId).stream()
                .filter(slot -> slot.getDiaSemana() == diaSemana)
                .toList();
        Set<String> ocupados = reservaHorarioRepository
                .findByQuadraHorarioIdInAndData(grade.stream().map(QuadraHorario::getId).toList(), data)
                .stream()
                .map(ReservaHorario::getQuadraHorarioId)
                .collect(Collectors.toSet());

        List<DisponibilidadeResponse.Slot> slots = grade.stream()
                .map(slot -> new DisponibilidadeResponse.Slot(slot.getId(), slot.getHoraInicio(),
                        slot.getHoraFim(), slot.getPreco(), !ocupados.contains(slot.getId())))
                .toList();
        return new DisponibilidadeResponse(data, slots);
    }

    // ---------- conflito ----------

    /** Monta o 409 com alternativas. Usado na pré-checagem e, pelo controller, na corrida. */
    @Transactional(readOnly = true)
    public ConflitoHorarioException conflito(String quadraId, LocalDate data) {
        List<DisponibilidadeResponse.Slot> livres = disponibilidade(quadraId, data).slots().stream()
                .filter(DisponibilidadeResponse.Slot::disponivel)
                .toList();
        String alternativas = livres.isEmpty()
                ? "Não há mais horários livres nesta data."
                : "Horários ainda livres nesta data: " + livres.stream()
                        .map(s -> s.horaInicio().format(HORA) + "–" + s.horaFim().format(HORA))
                        .collect(Collectors.joining(", ")) + ".";
        return new ConflitoHorarioException(
                "Um dos horários acabou de ser reservado por outro organizador. " + alternativas);
    }

    // ---------- privados ----------

    private Reserva buscarEntidade(String id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Reserva não encontrada: " + id));
    }

    private ReservaResponse montarResponse(Reserva reserva, Quadra quadra) {
        List<HorarioResponse> horarios = reservaHorarioRepository.findByReservaId(reserva.getId()).stream()
                .map(rh -> quadraHorarioRepository.findById(rh.getQuadraHorarioId()).orElseThrow())
                .map(HorarioResponse::new)
                .toList();
        return new ReservaResponse(reserva, quadra.getNome(), quadra.getContato(), horarios);
    }
}
