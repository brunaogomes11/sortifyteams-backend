package com.gomesdev.sortifyteams.domain.racha;

import com.gomesdev.sortifyteams.domain.esporte.Esporte;
import com.gomesdev.sortifyteams.domain.esporte.EsporteService;
import com.gomesdev.sortifyteams.domain.esporte.response.EsporteResponse;
import com.gomesdev.sortifyteams.domain.racha.request.ConcluirRachaRequest;
import com.gomesdev.sortifyteams.domain.racha.request.ParticipanteRequest;
import com.gomesdev.sortifyteams.domain.racha.request.RachaRequest;
import com.gomesdev.sortifyteams.domain.racha.response.ParticipanteResponse;
import com.gomesdev.sortifyteams.domain.racha.response.RachaResponse;
import com.gomesdev.sortifyteams.domain.racha.response.RachaResumoResponse;
import com.gomesdev.sortifyteams.domain.racha.response.TimeResponse;
import com.gomesdev.sortifyteams.domain.racha.sorteio.SorteioService;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RachaService {

    private final RachaRepository rachaRepository;
    private final ParticipanteRachaRepository participanteRepository;
    private final TimeRachaRepository timeRepository;
    private final EsporteService esporteService;
    private final UsuarioRepository usuarioRepository;
    private final SorteioService sorteioService;

    public RachaService(RachaRepository rachaRepository,
                        ParticipanteRachaRepository participanteRepository,
                        TimeRachaRepository timeRepository,
                        EsporteService esporteService,
                        UsuarioRepository usuarioRepository,
                        SorteioService sorteioService) {
        this.rachaRepository = rachaRepository;
        this.participanteRepository = participanteRepository;
        this.timeRepository = timeRepository;
        this.esporteService = esporteService;
        this.usuarioRepository = usuarioRepository;
        this.sorteioService = sorteioService;
    }

    @Transactional
    public RachaResponse criar(RachaRequest request, Usuario organizador) {
        esporteService.buscarEntidade(request.esporteId());
        Racha racha = rachaRepository.save(new Racha(request, organizador.getId()));
        return detalhar(racha.getId(), organizador);
    }

    @Transactional(readOnly = true)
    public List<RachaResumoResponse> listarDoUsuario(Usuario usuario) {
        return rachaRepository.findDoUsuario(usuario.getId()).stream()
                .map(racha -> {
                    Esporte esporte = esporteService.buscarEntidade(racha.getEsporteId());
                    long qtd = participanteRepository.countByRachaId(racha.getId());
                    boolean organizador = racha.getOrganizadorId().equals(usuario.getId());
                    return new RachaResumoResponse(racha, esporte.getNome(), esporte.getIcone(), qtd, organizador);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public RachaResponse detalhar(String rachaId, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirMembro(racha, usuario);
        return montarResponse(racha);
    }

    @Transactional
    public RachaResponse adicionarParticipante(String rachaId, ParticipanteRequest request, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        garantirAberto(racha);

        boolean temUsuario = request.usuarioId() != null && !request.usuarioId().isBlank();
        boolean temAvulso = request.nomeAvulso() != null && !request.nomeAvulso().isBlank();
        if (temUsuario == temAvulso) {
            throw new IllegalArgumentException("Informe usuarioId OU nomeAvulso (exatamente um).");
        }
        if (temUsuario) {
            if (!usuarioRepository.existsById(request.usuarioId())) {
                throw new EntityNotFoundException("Usuário não encontrado: " + request.usuarioId());
            }
            if (participanteRepository.existsByRachaIdAndUsuarioId(rachaId, request.usuarioId())) {
                throw new IllegalArgumentException("Usuário já está neste racha.");
            }
        }
        if (racha.getLimiteVagas() != null
                && participanteRepository.countByRachaId(rachaId) >= racha.getLimiteVagas()) {
            throw new IllegalArgumentException("O racha já atingiu o limite de vagas.");
        }

        participanteRepository.save(new ParticipanteRacha(request, rachaId));
        return montarResponse(racha);
    }

    @Transactional
    public RachaResponse removerParticipante(String rachaId, String participanteId, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        garantirAberto(racha);

        ParticipanteRacha participante = participanteRepository.findById(participanteId)
                .filter(p -> p.getRachaId().equals(rachaId))
                .orElseThrow(() -> new EntityNotFoundException("Participante não encontrado neste racha."));
        participanteRepository.delete(participante);
        return montarResponse(racha);
    }

    /** Sorteia (ou re-sorteia) os times do racha (FR-007). */
    @Transactional
    public RachaResponse sortear(String rachaId, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        garantirAberto(racha);

        Esporte esporte = esporteService.buscarEntidade(racha.getEsporteId());
        List<ParticipanteRacha> participantes = participanteRepository.findByRachaId(rachaId);

        List<SorteioService.JogadorSorteio> jogadores = participantes.stream()
                .map(p -> new SorteioService.JogadorSorteio(p.getId(), p.getNivelTecnico(),
                        esporte.isExigeGoleiro() && p.isEGoleiro()))
                .toList();

        List<SorteioService.TimeSorteado> sorteados = sorteioService.sortear(
                jogadores, racha.getQtdTimes(), racha.isBalancearNivel(),
                esporte.getJogadoresMinimosPorTime(), new Random());

        // Re-sorteio: limpa vínculos e times anteriores.
        participantes.forEach(p -> p.setTimeId(null));
        participanteRepository.saveAll(participantes);
        timeRepository.deleteByRachaId(rachaId);

        Map<String, ParticipanteRacha> porId = participantes.stream()
                .collect(Collectors.toMap(ParticipanteRacha::getId, Function.identity()));
        for (SorteioService.TimeSorteado sorteado : sorteados) {
            TimeRacha time = timeRepository.save(new TimeRacha(rachaId, sorteado.numero()));
            sorteado.jogadores().forEach(j -> porId.get(j.participanteId()).setTimeId(time.getId()));
        }
        participanteRepository.saveAll(participantes);

        return montarResponse(racha);
    }

    /** Conclui o racha, registrando a duração do cronômetro (C1). Alimenta contadores e dashboard (C14). */
    @Transactional
    public RachaResponse concluir(String rachaId, ConcluirRachaRequest request, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        garantirAberto(racha);

        racha.setStatus(StatusRachaEnum.CONCLUIDO);
        if (request != null && request.duracaoPartidaSeg() != null) {
            racha.setDuracaoPartidaSeg(request.duracaoPartidaSeg());
        }
        rachaRepository.save(racha);
        return montarResponse(racha);
    }

    /** Cancela o racha. (Cascata de reserva e notificações entram na Fase 6 — C10.) */
    @Transactional
    public void cancelar(String rachaId, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        garantirAberto(racha);

        racha.setStatus(StatusRachaEnum.CANCELADO);
        rachaRepository.save(racha);
    }

    // ---------- privados ----------

    private Racha buscarEntidade(String id) {
        return rachaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Racha não encontrado: " + id));
    }

    private void garantirOrganizador(Racha racha, Usuario usuario) {
        if (!racha.getOrganizadorId().equals(usuario.getId())) {
            throw new AccessDeniedException("Apenas o organizador pode executar esta ação.");
        }
    }

    private void garantirMembro(Racha racha, Usuario usuario) {
        boolean organizador = racha.getOrganizadorId().equals(usuario.getId());
        boolean participante = participanteRepository.existsByRachaIdAndUsuarioId(racha.getId(), usuario.getId());
        if (!organizador && !participante) {
            throw new AccessDeniedException("Você não participa deste racha.");
        }
    }

    private void garantirAberto(Racha racha) {
        if (racha.getStatus() != StatusRachaEnum.ABERTO) {
            throw new IllegalArgumentException("O racha não está mais aberto (status: %s).".formatted(racha.getStatus()));
        }
    }

    private RachaResponse montarResponse(Racha racha) {
        Esporte esporte = esporteService.buscarEntidade(racha.getEsporteId());
        List<ParticipanteRacha> participantes = participanteRepository.findByRachaId(racha.getId());
        List<TimeRacha> times = timeRepository.findByRachaIdOrderByNumero(racha.getId());

        Map<String, Integer> numeroPorTimeId = times.stream()
                .collect(Collectors.toMap(TimeRacha::getId, TimeRacha::getNumero));

        // Resolve nomes dos participantes cadastrados em lote (privacidade: só o nome).
        List<String> usuarioIds = participantes.stream()
                .map(ParticipanteRacha::getUsuarioId)
                .filter(id -> id != null)
                .toList();
        Map<String, String> nomePorUsuarioId = new HashMap<>();
        usuarioRepository.findAllById(usuarioIds)
                .forEach(u -> nomePorUsuarioId.put(u.getId(), u.getNomeCompleto()));

        List<ParticipanteResponse> participantesResponse = participantes.stream()
                .map(p -> new ParticipanteResponse(p,
                        nomePorUsuarioId.get(p.getUsuarioId()),
                        p.getTimeId() != null ? numeroPorTimeId.get(p.getTimeId()) : null))
                .toList();

        List<TimeResponse> timesResponse = times.stream()
                .map(t -> new TimeResponse(t.getNumero(), participantesResponse.stream()
                        .filter(p -> t.getNumero() == (p.timeNumero() != null ? p.timeNumero() : -1))
                        .toList()))
                .toList();

        return new RachaResponse(racha, new EsporteResponse(esporte), participantesResponse, timesResponse);
    }
}
