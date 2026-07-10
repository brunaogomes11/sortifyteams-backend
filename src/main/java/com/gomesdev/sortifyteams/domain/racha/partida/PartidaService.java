package com.gomesdev.sortifyteams.domain.racha.partida;

import com.gomesdev.sortifyteams.domain.racha.ParticipanteRacha;
import com.gomesdev.sortifyteams.domain.racha.ParticipanteRachaRepository;
import com.gomesdev.sortifyteams.domain.racha.Racha;
import com.gomesdev.sortifyteams.domain.racha.RachaRepository;
import com.gomesdev.sortifyteams.domain.racha.TimeRacha;
import com.gomesdev.sortifyteams.domain.racha.TimeRachaRepository;
import com.gomesdev.sortifyteams.domain.racha.partida.request.EncerrarPartidaRequest;
import com.gomesdev.sortifyteams.domain.racha.partida.request.GolRequest;
import com.gomesdev.sortifyteams.domain.racha.partida.request.PartidaRequest;
import com.gomesdev.sortifyteams.domain.racha.partida.response.GolResponse;
import com.gomesdev.sortifyteams.domain.racha.partida.response.PartidaResponse;
import com.gomesdev.sortifyteams.domain.racha.partida.response.RachaAoVivoResponse;
import com.gomesdev.sortifyteams.domain.racha.partida.response.SugestaoPartidaResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository;
import com.gomesdev.sortifyteams.enums.CriterioEmpateEnum;
import com.gomesdev.sortifyteams.enums.StatusPartidaEnum;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fluxo ao vivo do racha (C1 evoluído): partidas na dinâmica "vencedor fica",
 * gols marcados por qualquer membro e snapshot único ({@link RachaAoVivoResponse})
 * que alimenta tanto o GET /ao-vivo quanto o tópico STOMP /topic/rachas/{id}.
 */
@Service
public class PartidaService {

    private static final int NUMERO_GRUPO_GOLEIROS = 0;

    private final RachaRepository rachaRepository;
    private final ParticipanteRachaRepository participanteRepository;
    private final TimeRachaRepository timeRepository;
    private final PartidaRachaRepository partidaRepository;
    private final GolPartidaRepository golRepository;
    private final UsuarioRepository usuarioRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PartidaService(RachaRepository rachaRepository,
                          ParticipanteRachaRepository participanteRepository,
                          TimeRachaRepository timeRepository,
                          PartidaRachaRepository partidaRepository,
                          GolPartidaRepository golRepository,
                          UsuarioRepository usuarioRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.rachaRepository = rachaRepository;
        this.participanteRepository = participanteRepository;
        this.timeRepository = timeRepository;
        this.partidaRepository = partidaRepository;
        this.golRepository = golRepository;
        this.usuarioRepository = usuarioRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /** Snapshot ao vivo/histórico — funciona em qualquer status (CONCLUIDO = histórico). */
    @Transactional(readOnly = true)
    public RachaAoVivoResponse aoVivo(String rachaId, Usuario usuario) {
        Racha racha = buscarRacha(rachaId);
        garantirMembro(racha, usuario);
        return montarAoVivo(racha);
    }

    @Transactional
    public RachaAoVivoResponse criarPartida(String rachaId, PartidaRequest request, Usuario usuario) {
        Racha racha = buscarRacha(rachaId);
        garantirOrganizador(racha, usuario);
        garantirEmAndamento(racha);

        if (partidaRepository.findAtiva(rachaId).isPresent()) {
            throw new IllegalArgumentException("Encerre a partida atual antes de começar outra.");
        }
        if (request.timeNumeroA().equals(request.timeNumeroB())) {
            throw new IllegalArgumentException("Escolha dois times diferentes para a partida.");
        }
        Set<Integer> numerosValidos = numerosDeTimesJogaveis(rachaId);
        for (int numero : List.of(request.timeNumeroA(), request.timeNumeroB())) {
            if (!numerosValidos.contains(numero)) {
                throw new IllegalArgumentException("O time %d não existe neste racha.".formatted(numero));
            }
        }

        Integer duracao = request.duracaoPrevistaSeg() != null
                ? request.duracaoPrevistaSeg() : racha.getDuracaoPartidaSeg();
        if (request.duracaoPrevistaSeg() != null) {
            // Mantém o campo legado do racha como "última duração usada" (prefill da próxima).
            racha.setDuracaoPartidaSeg(request.duracaoPrevistaSeg());
            rachaRepository.save(racha);
        }

        partidaRepository.save(new PartidaRacha(rachaId, request.timeNumeroA(), request.timeNumeroB(), duracao));
        return broadcast(racha);
    }

    @Transactional
    public RachaAoVivoResponse encerrarPartida(String rachaId, String partidaId,
                                               EncerrarPartidaRequest request, Usuario usuario) {
        Racha racha = buscarRacha(rachaId);
        garantirOrganizador(racha, usuario);

        PartidaRacha partida = buscarPartida(rachaId, partidaId);
        if (partida.getStatus() != StatusPartidaEnum.EM_ANDAMENTO) {
            throw new IllegalArgumentException("A partida já foi encerrada.");
        }
        Integer vencedorInformado = request != null ? request.vencedorTimeNumero() : null;
        if (vencedorInformado != null
                && vencedorInformado != partida.getTimeNumeroA()
                && vencedorInformado != partida.getTimeNumeroB()) {
            throw new IllegalArgumentException("O time vencedor informado não está em quadra nesta partida.");
        }
        encerrar(racha, partida, vencedorInformado);
        return broadcast(racha);
    }

    @Transactional
    public RachaAoVivoResponse registrarGol(String rachaId, String partidaId, GolRequest request, Usuario usuario) {
        Racha racha = buscarRacha(rachaId);
        garantirMembro(racha, usuario);
        garantirEmAndamento(racha);

        PartidaRacha partida = buscarPartida(rachaId, partidaId);
        if (partida.getStatus() != StatusPartidaEnum.EM_ANDAMENTO) {
            throw new IllegalArgumentException("A partida não está em andamento.");
        }
        int timeNumero = request.timeNumero();
        if (timeNumero != partida.getTimeNumeroA() && timeNumero != partida.getTimeNumeroB()) {
            throw new IllegalArgumentException("Esse time não está em quadra nesta partida.");
        }
        if (request.participanteId() != null && !request.participanteId().isBlank()) {
            participanteRepository.findById(request.participanteId())
                    .filter(p -> p.getRachaId().equals(rachaId))
                    .orElseThrow(() -> new EntityNotFoundException("Participante não encontrado neste racha."));
        }

        int tempoSeg = (int) Math.max(0, Duration.between(partida.getIniciadaEm(), LocalDateTime.now()).getSeconds());
        String participanteId = (request.participanteId() != null && !request.participanteId().isBlank())
                ? request.participanteId() : null;
        golRepository.save(new GolPartida(partida.getId(), rachaId, timeNumero,
                participanteId, usuario.getId(), tempoSeg));
        return broadcast(racha);
    }

    @Transactional
    public RachaAoVivoResponse removerGol(String rachaId, String golId, Usuario usuario) {
        Racha racha = buscarRacha(rachaId);
        garantirMembro(racha, usuario);
        garantirEmAndamento(racha);

        GolPartida gol = golRepository.findById(golId)
                .filter(g -> g.getRachaId().equals(rachaId))
                .orElseThrow(() -> new EntityNotFoundException("Gol não encontrado neste racha."));
        boolean organizador = racha.getOrganizadorId().equals(usuario.getId());
        if (!organizador && !gol.getRegistradoPorUsuarioId().equals(usuario.getId())) {
            throw new AccessDeniedException("Apenas o organizador ou quem registrou o gol pode removê-lo.");
        }
        golRepository.delete(gol);
        return broadcast(racha);
    }

    /** Encerra a partida ativa, se houver — usado ao concluir o racha e na expiração. */
    @Transactional
    public void encerrarPartidaAtivaSeExistir(String rachaId) {
        partidaRepository.findAtiva(rachaId).ifPresent(partida -> {
            Racha racha = buscarRacha(rachaId);
            encerrar(racha, partida, null);
        });
    }

    /** Publica o snapshot no tópico do racha e o devolve (resposta REST = mensagem STOMP). */
    @Transactional
    public RachaAoVivoResponse broadcast(Racha racha) {
        RachaAoVivoResponse snapshot = montarAoVivo(racha);
        messagingTemplate.convertAndSend("/topic/rachas/" + racha.getId(), snapshot);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public RachaAoVivoResponse montarAoVivo(Racha racha) {
        List<PartidaRacha> partidas = partidaRepository.findByRachaIdOrderByIniciadaEmAsc(racha.getId());
        List<GolPartida> gols = golRepository.findByRachaIdOrderByCriadoEmAsc(racha.getId());
        Map<String, String> nomePorParticipanteId = nomesDosParticipantes(racha.getId());

        Map<String, List<GolResponse>> golsPorPartida = gols.stream()
                .map(g -> new GolResponse(g.getId(), g.getPartidaId(), g.getTimeNumero(),
                        g.getParticipanteId(),
                        g.getParticipanteId() != null ? nomePorParticipanteId.get(g.getParticipanteId()) : null,
                        g.getRegistradoPorUsuarioId(), g.getTempoSeg(), g.getCriadoEm()))
                .collect(Collectors.groupingBy(GolResponse::partidaId));

        List<PartidaResponse> partidasResponse = new ArrayList<>();
        for (int i = 0; i < partidas.size(); i++) {
            PartidaRacha partida = partidas.get(i);
            List<GolResponse> golsDaPartida = golsPorPartida.getOrDefault(partida.getId(), List.of());
            int placarA = (int) golsDaPartida.stream().filter(g -> g.timeNumero() == partida.getTimeNumeroA()).count();
            int placarB = (int) golsDaPartida.stream().filter(g -> g.timeNumero() == partida.getTimeNumeroB()).count();
            partidasResponse.add(new PartidaResponse(partida.getId(), i + 1,
                    partida.getTimeNumeroA(), partida.getTimeNumeroB(), partida.getStatus(),
                    partida.getDuracaoPrevistaSeg(), partida.getIniciadaEm(), partida.getEncerradaEm(),
                    placarA, placarB, partida.getVencedorTimeNumero(), golsDaPartida));
        }

        PartidaResponse partidaAtual = partidasResponse.stream()
                .filter(p -> p.status() == StatusPartidaEnum.EM_ANDAMENTO)
                .findFirst().orElse(null);
        List<PartidaResponse> encerradas = partidasResponse.stream()
                .filter(p -> p.status() == StatusPartidaEnum.ENCERRADA)
                .toList();

        SugestaoPartidaResponse sugestao = null;
        if (racha.getStatus() == StatusRachaEnum.EM_ANDAMENTO && partidaAtual == null) {
            sugestao = sugerirProximaPartida(racha, encerradas);
        }

        return new RachaAoVivoResponse(racha.getId(), racha.getStatus(), LocalDateTime.now(),
                racha.getIniciadoEm(), racha.getDuracaoPartidaSeg(),
                racha.getCriterioEmpateZero(), racha.getCriterioEmpateGols(),
                partidaAtual, encerradas, sugestao);
    }

    // ---------- privados ----------

    private void encerrar(Racha racha, PartidaRacha partida, Integer vencedorInformado) {
        List<GolPartida> gols = golRepository.findByRachaIdOrderByCriadoEmAsc(racha.getId()).stream()
                .filter(g -> g.getPartidaId().equals(partida.getId()))
                .toList();
        int placarA = (int) gols.stream().filter(g -> g.getTimeNumero() == partida.getTimeNumeroA()).count();
        int placarB = (int) gols.stream().filter(g -> g.getTimeNumero() == partida.getTimeNumeroB()).count();

        partida.setVencedorTimeNumero(resolverVencedor(racha, partida, placarA, placarB, vencedorInformado));
        partida.setStatus(StatusPartidaEnum.ENCERRADA);
        partida.setEncerradaEm(LocalDateTime.now());
        partidaRepository.save(partida);
    }

    /**
     * Placar diferente: o maior vence. Empate: aplica o critério configurado no
     * racha para aquele placar (0x0 vs com gols). "Quem fica"/"quem entra" é
     * resolvido pelo incumbente derivado do histórico; se o organizador informou
     * um vencedor (pênaltis), ele prevalece sobre o critério.
     */
    private Integer resolverVencedor(Racha racha, PartidaRacha partida,
                                     int placarA, int placarB, Integer vencedorInformado) {
        if (placarA != placarB) {
            return placarA > placarB ? partida.getTimeNumeroA() : partida.getTimeNumeroB();
        }
        if (vencedorInformado != null) {
            return vencedorInformado;
        }
        CriterioEmpateEnum criterio = placarA == 0
                ? racha.getCriterioEmpateZero() : racha.getCriterioEmpateGols();
        Integer incumbente = incumbenteDe(partida);
        return switch (criterio) {
            case TIME_QUE_FICA -> incumbente;
            case TIME_QUE_ENTRA -> incumbente == null ? null
                    : (incumbente == partida.getTimeNumeroA()
                            ? partida.getTimeNumeroB() : partida.getTimeNumeroA());
            // Gol de ouro encerra desempatado por definição; empatado mesmo assim = empate.
            // Pênaltis sem vencedor informado também fica empate.
            case AMBOS_SAEM, GOL_DE_OURO, PENALTIS -> null;
        };
    }

    /**
     * O incumbente ("quem fica") é o time desta partida que também jogou a
     * partida encerrada imediatamente anterior. Ambíguo (primeira partida,
     * rematch dos mesmos times ou dois entrantes) devolve nulo.
     */
    private Integer incumbenteDe(PartidaRacha partida) {
        PartidaRacha anterior = partidaRepository.findByRachaIdOrderByIniciadaEmAsc(partida.getRachaId()).stream()
                .filter(p -> p.getStatus() == StatusPartidaEnum.ENCERRADA)
                .filter(p -> !p.getId().equals(partida.getId()))
                .filter(p -> p.getIniciadaEm().isBefore(partida.getIniciadaEm()))
                .reduce((primeira, ultima) -> ultima)
                .orElse(null);
        if (anterior == null) {
            return null;
        }
        boolean aJogouAnterior = jogouNaPartida(partida.getTimeNumeroA(), anterior);
        boolean bJogouAnterior = jogouNaPartida(partida.getTimeNumeroB(), anterior);
        if (aJogouAnterior == bJogouAnterior) {
            return null; // rematch ou dois entrantes — sem incumbente claro
        }
        return aJogouAnterior ? partida.getTimeNumeroA() : partida.getTimeNumeroB();
    }

    private boolean jogouNaPartida(int timeNumero, PartidaRacha partida) {
        return timeNumero == partida.getTimeNumeroA() || timeNumero == partida.getTimeNumeroB();
    }

    /**
     * Sugestão da próxima partida: quem fica = vencedor da última; entrante =
     * primeiro da fila (time fora de quadra há mais tempo; quem nunca jogou tem
     * prioridade). Empate sem vencedor com critério AMBOS_SAEM sugere dois
     * entrantes; empate ambíguo não sugere nada (o organizador escolhe).
     */
    private SugestaoPartidaResponse sugerirProximaPartida(Racha racha, List<PartidaResponse> encerradas) {
        List<Integer> numeros = numerosDeTimesJogaveis(racha.getId()).stream().sorted().toList();
        if (numeros.size() < 2) {
            return null;
        }
        if (encerradas.isEmpty()) {
            return new SugestaoPartidaResponse(numeros.get(0), numeros.get(1));
        }

        PartidaResponse ultima = encerradas.get(encerradas.size() - 1);
        List<Integer> fila = filaDeEntrantes(numeros, encerradas, ultima);
        Integer vencedor = ultima.vencedorTimeNumero();

        if (vencedor != null) {
            if (fila.isEmpty()) {
                // Só 2 times: rematch, vencedor continua no lado A.
                return new SugestaoPartidaResponse(vencedor,
                        vencedor == ultima.timeNumeroA() ? ultima.timeNumeroB() : ultima.timeNumeroA());
            }
            return new SugestaoPartidaResponse(vencedor, fila.get(0));
        }

        boolean empateZero = ultima.placarA() == 0 && ultima.placarB() == 0;
        CriterioEmpateEnum criterio = empateZero
                ? racha.getCriterioEmpateZero() : racha.getCriterioEmpateGols();
        if (criterio == CriterioEmpateEnum.AMBOS_SAEM && fila.size() >= 2) {
            return new SugestaoPartidaResponse(fila.get(0), fila.get(1));
        }
        return null;
    }

    /** Times fora da última partida, ordenados por "fora de quadra há mais tempo". */
    private List<Integer> filaDeEntrantes(List<Integer> numeros, List<PartidaResponse> encerradas,
                                          PartidaResponse ultima) {
        Map<Integer, Integer> ultimaAparicao = new HashMap<>();
        for (int i = 0; i < encerradas.size(); i++) {
            ultimaAparicao.put(encerradas.get(i).timeNumeroA(), i);
            ultimaAparicao.put(encerradas.get(i).timeNumeroB(), i);
        }
        return numeros.stream()
                .filter(n -> n != ultima.timeNumeroA() && n != ultima.timeNumeroB())
                .sorted(java.util.Comparator
                        .comparingInt((Integer n) -> ultimaAparicao.getOrDefault(n, -1))
                        .thenComparingInt(n -> n))
                .toList();
    }

    private Set<Integer> numerosDeTimesJogaveis(String rachaId) {
        return timeRepository.findByRachaIdOrderByNumero(rachaId).stream()
                .map(TimeRacha::getNumero)
                .filter(n -> n != NUMERO_GRUPO_GOLEIROS)
                .collect(Collectors.toSet());
    }

    private Map<String, String> nomesDosParticipantes(String rachaId) {
        List<ParticipanteRacha> participantes = participanteRepository.findByRachaId(rachaId);
        List<String> usuarioIds = participantes.stream()
                .map(ParticipanteRacha::getUsuarioId)
                .filter(id -> id != null)
                .toList();
        Map<String, String> nomePorUsuarioId = new HashMap<>();
        usuarioRepository.findAllById(usuarioIds)
                .forEach(u -> nomePorUsuarioId.put(u.getId(), u.getNomeCompleto()));
        Map<String, String> nomePorParticipanteId = new HashMap<>();
        for (ParticipanteRacha p : participantes) {
            nomePorParticipanteId.put(p.getId(), p.getNomeAvulso() != null
                    ? p.getNomeAvulso() : nomePorUsuarioId.get(p.getUsuarioId()));
        }
        return nomePorParticipanteId;
    }

    private Racha buscarRacha(String id) {
        return rachaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Racha não encontrado: " + id));
    }

    private PartidaRacha buscarPartida(String rachaId, String partidaId) {
        return partidaRepository.findById(partidaId)
                .filter(p -> p.getRachaId().equals(rachaId))
                .orElseThrow(() -> new EntityNotFoundException("Partida não encontrada neste racha."));
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

    private void garantirEmAndamento(Racha racha) {
        if (racha.getStatus() != StatusRachaEnum.EM_ANDAMENTO) {
            throw new IllegalArgumentException(
                    "O racha não está ao vivo (status: %s).".formatted(racha.getStatus()));
        }
    }
}
