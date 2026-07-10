package com.gomesdev.sortifyteams.domain.reserva;

import com.gomesdev.sortifyteams.domain.notificacao.NotificacaoService;
import com.gomesdev.sortifyteams.domain.quadra.Quadra;
import com.gomesdev.sortifyteams.domain.quadra.QuadraHorario;
import com.gomesdev.sortifyteams.domain.quadra.QuadraHorarioRepository;
import com.gomesdev.sortifyteams.domain.quadra.QuadraPublicaService;
import com.gomesdev.sortifyteams.domain.quadra.QuadraRepository;
import com.gomesdev.sortifyteams.domain.quadra.response.HorarioResponse;
import com.gomesdev.sortifyteams.domain.racha.ParticipanteRacha;
import com.gomesdev.sortifyteams.domain.racha.ParticipanteRachaRepository;
import com.gomesdev.sortifyteams.domain.racha.Racha;
import com.gomesdev.sortifyteams.domain.racha.RachaLocalizacaoService;
import com.gomesdev.sortifyteams.domain.racha.RachaRepository;
import com.gomesdev.sortifyteams.domain.reserva.request.ReservaRequest;
import com.gomesdev.sortifyteams.domain.reserva.response.AgendaItemResponse;
import com.gomesdev.sortifyteams.domain.reserva.response.DisponibilidadeResponse;
import com.gomesdev.sortifyteams.domain.reserva.response.ReservaResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository;
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
import java.time.format.TextStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Reserva de quadra (Fluxo 4) — regra crítica de conflito C8/FR-009. */
@Service
public class ReservaService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Status que ainda ocupam o slot e contam como reserva "viva". */
    private static final List<StatusReservaEnum> ATIVAS =
            List.of(StatusReservaEnum.PENDENTE, StatusReservaEnum.CONFIRMADA);

    private final ReservaRepository reservaRepository;
    private final ReservaHorarioRepository reservaHorarioRepository;
    private final QuadraHorarioRepository quadraHorarioRepository;
    private final QuadraPublicaService quadraPublicaService;
    private final QuadraRepository quadraRepository;
    private final RachaRepository rachaRepository;
    private final ParticipanteRachaRepository participanteRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;
    private final RachaLocalizacaoService rachaLocalizacaoService;

    public ReservaService(ReservaRepository reservaRepository,
                          ReservaHorarioRepository reservaHorarioRepository,
                          QuadraHorarioRepository quadraHorarioRepository,
                          QuadraPublicaService quadraPublicaService,
                          QuadraRepository quadraRepository,
                          RachaRepository rachaRepository,
                          ParticipanteRachaRepository participanteRepository,
                          UsuarioRepository usuarioRepository,
                          NotificacaoService notificacaoService,
                          RachaLocalizacaoService rachaLocalizacaoService) {
        this.reservaRepository = reservaRepository;
        this.reservaHorarioRepository = reservaHorarioRepository;
        this.quadraHorarioRepository = quadraHorarioRepository;
        this.quadraPublicaService = quadraPublicaService;
        this.quadraRepository = quadraRepository;
        this.rachaRepository = rachaRepository;
        this.participanteRepository = participanteRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacaoService = notificacaoService;
        this.rachaLocalizacaoService = rachaLocalizacaoService;
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
        if (reservaRepository.existsByRachaIdAndStatusIn(racha.getId(), ATIVAS)) {
            throw new IllegalArgumentException(
                    "Este racha já tem uma reserva em andamento (pendente ou confirmada).");
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
                                        request.data().format(DATA_BR),
                                        request.data().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.of("pt", "BR"))));
            }
        }

        // Pré-checagem amigável; a UNIQUE (quadra_horario_id, data) é o guarda-costas
        // contra corrida entre transações (C8).
        List<ReservaHorario> ocupados = reservaHorarioRepository
                .findByQuadraHorarioIdInAndData(List.copyOf(idsUnicos), request.data());
        if (!ocupados.isEmpty()) {
            throw conflito(quadra.getId(), request.data(), false);
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
        // Herda as coordenadas da quadra para a busca pública por proximidade (FIX 1) —
        // sempre que a quadra tiver coordenadas, mesmo em racha privado (inofensivo e mantém consistência).
        if (quadra.getLatitude() != null) {
            racha.setLatitude(quadra.getLatitude());
            racha.setLongitude(quadra.getLongitude());
            racha.setCidade(quadra.getCidade());
        }
        rachaRepository.save(racha);

        // A reserva nasce PENDENTE: o slot já fica bloqueado, mas o dono precisa
        // aceitar. Só o dono é avisado agora; organizador e jogadores são
        // notificados quando (e se) o dono confirmar.
        notificacaoService.notificar(quadra.getDonoId(), "RESERVA_SOLICITADA",
                "Nova solicitação de reserva na " + quadra.getNome(),
                "%s pediu %s para %s. Aceite ou recuse na sua agenda."
                        .formatted(organizador.getNomeCompleto(),
                                slots.size() == 1 ? "1 horário" : slots.size() + " horários",
                                request.data().format(DATA_BR)));

        return montarResponse(reserva, quadra);
    }

    /** Dono aceita a solicitação: PENDENTE → CONFIRMADA. Avisa organizador e jogadores. */
    @Transactional
    public ReservaResponse aceitar(String reservaId, Usuario dono) {
        Reserva reserva = buscarEntidade(reservaId);
        Quadra quadra = quadraRepository.findById(reserva.getQuadraId())
                .orElseThrow(() -> new EntityNotFoundException("Quadra não encontrada: " + reserva.getQuadraId()));
        if (!quadra.getDonoId().equals(dono.getId())) {
            throw new AccessDeniedException("Esta reserva é de uma quadra de outro dono.");
        }
        if (reserva.getStatus() != StatusReservaEnum.PENDENTE) {
            throw new IllegalArgumentException("Só dá para aceitar uma reserva pendente.");
        }

        reserva.setStatus(StatusReservaEnum.CONFIRMADA);
        reservaRepository.save(reserva);

        Racha racha = rachaRepository.findById(reserva.getRachaId()).orElseThrow();
        String corpo = "O racha foi confirmado para %s às %s na %s."
                .formatted(reserva.getData().format(DATA_BR),
                        racha.getHorario() != null ? racha.getHorario().format(HORA) : "?",
                        quadra.getNome());
        notificacaoService.notificar(racha.getOrganizadorId(), "RESERVA_CONFIRMADA",
                "Reserva confirmada na " + quadra.getNome(), corpo);
        participanteRepository.findByRachaId(racha.getId()).stream()
                .map(ParticipanteRacha::getUsuarioId)
                .filter(id -> id != null && !id.equals(racha.getOrganizadorId()))
                .distinct()
                .forEach(usuarioId -> notificacaoService.notificar(
                        usuarioId, "RESERVA_CONFIRMADA", "Racha confirmado", corpo));

        return montarResponse(reserva, quadra);
    }

    @Transactional(readOnly = true)
    public ReservaResponse detalhar(String reservaId, Usuario usuario) {
        Reserva reserva = buscarEntidade(reservaId);
        // Quadra pode ter sido desativada nesse meio-tempo — isso não pode esconder a reserva (FIX 3).
        Quadra quadra = quadraRepository.findById(reserva.getQuadraId())
                .orElseThrow(() -> new EntityNotFoundException("Quadra não encontrada: " + reserva.getQuadraId()));
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
        // Cancela tanto a solicitação pendente quanto a reserva já confirmada.
        if (!ATIVAS.contains(reserva.getStatus())) {
            throw new IllegalArgumentException("A reserva já está cancelada.");
        }

        reserva.setStatus(StatusReservaEnum.CANCELADA_ORGANIZADOR);
        reservaRepository.save(reserva);
        reservaHorarioRepository.deleteByReservaId(reserva.getId());

        racha.setQuadraId(null);
        if (racha.isPublico()) {
            rachaLocalizacaoService.resolver(racha);
        }
        rachaRepository.save(racha);

        // Quadra pode ter sido desativada nesse meio-tempo — isso não pode barrar o cancelamento (FIX 3).
        Quadra quadra = quadraRepository.findById(reserva.getQuadraId())
                .orElseThrow(() -> new EntityNotFoundException("Quadra não encontrada: " + reserva.getQuadraId()));
        notificacaoService.notificar(quadra.getDonoId(), "RESERVA_CANCELADA",
                "Reserva cancelada na " + quadra.getNome(),
                "O organizador cancelou a reserva de %s.".formatted(reserva.getData().format(DATA_BR)));
    }

    /** C10: racha cancelado cancela a reserva ativa (pendente/confirmada) e notifica o dono. */
    @Transactional
    public void cancelarPorCancelamentoDoRacha(String rachaId) {
        for (Reserva reserva : reservaRepository.findByRachaIdAndStatusIn(rachaId, ATIVAS)) {
            reserva.setStatus(StatusReservaEnum.CANCELADA_ORGANIZADOR);
            reservaRepository.save(reserva);
            reservaHorarioRepository.deleteByReservaId(reserva.getId());

            Quadra quadra = quadraRepository.findById(reserva.getQuadraId()).orElseThrow();
            notificacaoService.notificar(quadra.getDonoId(), "RESERVA_CANCELADA",
                    "Reserva cancelada na " + quadra.getNome(),
                    "O racha foi cancelado pelo organizador; o horário de %s foi liberado."
                            .formatted(reserva.getData().format(DATA_BR)));
        }
    }

    /** C10: dono recusa (pendente) ou cancela (confirmada) — libera o slot e avisa a galera. */
    @Transactional
    public void cancelarPeloDono(String reservaId, Usuario dono) {
        Reserva reserva = buscarEntidade(reservaId);
        Quadra quadra = quadraRepository.findById(reserva.getQuadraId()).orElseThrow();
        if (!quadra.getDonoId().equals(dono.getId())) {
            throw new AccessDeniedException("Esta reserva é de uma quadra de outro dono.");
        }
        if (!ATIVAS.contains(reserva.getStatus())) {
            throw new IllegalArgumentException("Esta reserva já não está mais ativa.");
        }
        boolean eraPendente = reserva.getStatus() == StatusReservaEnum.PENDENTE;

        reserva.setStatus(StatusReservaEnum.CANCELADA_DONO);
        reservaRepository.save(reserva);
        reservaHorarioRepository.deleteByReservaId(reserva.getId());

        Racha racha = rachaRepository.findById(reserva.getRachaId()).orElseThrow();
        racha.setQuadraId(null);
        if (racha.isPublico()) {
            rachaLocalizacaoService.resolver(racha);
        }
        rachaRepository.save(racha);

        String titulo = eraPendente
                ? "Solicitação recusada pela " + quadra.getNome()
                : "Reserva cancelada pela " + quadra.getNome();
        String corpo = eraPendente
                ? "A quadra recusou o pedido de reserva de %s. Escolha outra quadra ou horário."
                        .formatted(reserva.getData().format(DATA_BR))
                : "O dono da quadra cancelou a reserva de %s. Escolha outra quadra ou horário."
                        .formatted(reserva.getData().format(DATA_BR));
        notificacaoService.notificar(racha.getOrganizadorId(), "RESERVA_CANCELADA_DONO", titulo, corpo);
        participanteRepository.findByRachaId(racha.getId()).stream()
                .map(ParticipanteRacha::getUsuarioId)
                .filter(id -> id != null && !id.equals(racha.getOrganizadorId()))
                .distinct()
                .forEach(usuarioId -> notificacaoService.notificar(
                        usuarioId, "RESERVA_CANCELADA_DONO", titulo, corpo));
    }

    /**
     * Dono desativou a quadra: cancela todas as reservas confirmadas futuras
     * dessa quadra, desvincula o racha e notifica organizador + jogadores (FIX 3).
     */
    @Transactional
    public void cancelarPorDesativacaoDaQuadra(String quadraId) {
        List<Reserva> futuras = reservaRepository.findByQuadraIdAndStatusInAndDataGreaterThanEqual(
                quadraId, ATIVAS, LocalDate.now());
        if (futuras.isEmpty()) {
            return;
        }
        Quadra quadra = quadraRepository.findById(quadraId)
                .orElseThrow(() -> new EntityNotFoundException("Quadra não encontrada: " + quadraId));

        for (Reserva reserva : futuras) {
            reserva.setStatus(StatusReservaEnum.CANCELADA_DONO);
            reservaRepository.save(reserva);
            reservaHorarioRepository.deleteByReservaId(reserva.getId());

            Racha racha = rachaRepository.findById(reserva.getRachaId()).orElseThrow();
            racha.setQuadraId(null);
            if (racha.isPublico()) {
                rachaLocalizacaoService.resolver(racha);
            }
            rachaRepository.save(racha);

            String titulo = "Reserva cancelada pela " + quadra.getNome();
            String corpo = "O dono desativou a quadra %s; a reserva de %s foi cancelada. Escolha outra quadra."
                    .formatted(quadra.getNome(), reserva.getData().format(DATA_BR));
            notificacaoService.notificar(racha.getOrganizadorId(), "RESERVA_CANCELADA_DONO", titulo, corpo);
            participanteRepository.findByRachaId(racha.getId()).stream()
                    .map(ParticipanteRacha::getUsuarioId)
                    .filter(id -> id != null && !id.equals(racha.getOrganizadorId()))
                    .distinct()
                    .forEach(usuarioId -> notificacaoService.notificar(
                            usuarioId, "RESERVA_CANCELADA_DONO", titulo, corpo));
        }
    }

    /** Agenda do dono (T034): reservas nas quadras dele no período. */
    @Transactional(readOnly = true)
    public List<AgendaItemResponse> agenda(Usuario dono, LocalDate de, LocalDate ate) {
        return reservaRepository.agendaDoDono(dono.getId(), de, ate).stream()
                .map(reserva -> {
                    Quadra quadra = quadraRepository.findById(reserva.getQuadraId()).orElseThrow();
                    Racha racha = rachaRepository.findById(reserva.getRachaId()).orElseThrow();
                    Usuario organizador = usuarioRepository.findById(racha.getOrganizadorId()).orElseThrow();
                    // Slots podem ter sumido da grade (redefinição de horários) — omite o
                    // detalhe em vez de derrubar a tela com 500 (FIX 2).
                    List<HorarioResponse> horarios = reservaHorarioRepository
                            .findByReservaId(reserva.getId()).stream()
                            .flatMap(rh -> quadraHorarioRepository.findById(rh.getQuadraHorarioId()).stream())
                            .map(HorarioResponse::new)
                            .toList();
                    return new AgendaItemResponse(reserva.getId(), quadra.getId(), quadra.getNome(),
                            reserva.getData(), reserva.getStatus(), reserva.getPrecoTotal(), horarios,
                            organizador.getNomeCompleto(), organizador.getContato());
                })
                .toList();
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

    /**
     * Monta o 409 com alternativas.
     *
     * @param corrida {@code false} na pré-checagem dentro da própria criação (caminho comum);
     *                {@code true} quando chamado pelo controller a partir do catch de
     *                {@link org.springframework.dao.DataIntegrityViolationException} (corrida real
     *                entre transações).
     */
    @Transactional(readOnly = true)
    public ConflitoHorarioException conflito(String quadraId, LocalDate data, boolean corrida) {
        List<DisponibilidadeResponse.Slot> livres = disponibilidade(quadraId, data).slots().stream()
                .filter(DisponibilidadeResponse.Slot::disponivel)
                .toList();
        String alternativas = livres.isEmpty()
                ? "Não há mais horários livres nesta data."
                : "Horários ainda livres nesta data: " + livres.stream()
                        .map(s -> s.horaInicio().format(HORA) + "–" + s.horaFim().format(HORA))
                        .collect(Collectors.joining(", ")) + ".";
        String mensagem = corrida
                ? "Um dos horários acabou de ser reservado por outro organizador. " + alternativas
                : "Um dos horários já está reservado nesta data. " + alternativas;
        return new ConflitoHorarioException(mensagem);
    }

    // ---------- privados ----------

    private Reserva buscarEntidade(String id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Reserva não encontrada: " + id));
    }

    private ReservaResponse montarResponse(Reserva reserva, Quadra quadra) {
        // Slots podem ter sumido da grade (redefinição de horários) — omite o
        // detalhe em vez de derrubar a tela com 500 (FIX 2).
        List<HorarioResponse> horarios = reservaHorarioRepository.findByReservaId(reserva.getId()).stream()
                .flatMap(rh -> quadraHorarioRepository.findById(rh.getQuadraHorarioId()).stream())
                .map(HorarioResponse::new)
                .toList();
        return new ReservaResponse(reserva, quadra.getNome(), quadra.getContato(), horarios);
    }
}
