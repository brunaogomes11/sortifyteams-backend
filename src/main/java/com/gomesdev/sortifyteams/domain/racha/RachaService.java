package com.gomesdev.sortifyteams.domain.racha;

import com.gomesdev.sortifyteams.domain.esporte.Esporte;
import com.gomesdev.sortifyteams.domain.esporte.EsporteService;
import com.gomesdev.sortifyteams.domain.esporte.response.EsporteResponse;
import com.gomesdev.sortifyteams.domain.notificacao.NotificacaoService;
import com.gomesdev.sortifyteams.domain.racha.request.ConcluirRachaRequest;
import com.gomesdev.sortifyteams.domain.racha.request.EntrarConviteRequest;
import com.gomesdev.sortifyteams.domain.racha.request.ParticipanteRequest;
import com.gomesdev.sortifyteams.domain.racha.request.RachaConfigRequest;
import com.gomesdev.sortifyteams.domain.racha.request.RachaRequest;
import com.gomesdev.sortifyteams.domain.racha.request.TimesRequest;
import com.gomesdev.sortifyteams.domain.racha.response.ConviteResponse;
import com.gomesdev.sortifyteams.domain.racha.response.ParticipanteResponse;
import com.gomesdev.sortifyteams.domain.racha.response.RachaPublicoResponse;
import com.gomesdev.sortifyteams.domain.racha.response.RachaResponse;
import com.gomesdev.sortifyteams.domain.racha.response.RachaResumoResponse;
import com.gomesdev.sortifyteams.domain.racha.response.TimeResponse;
import com.gomesdev.sortifyteams.domain.racha.partida.PartidaService;
import com.gomesdev.sortifyteams.domain.racha.partida.response.RachaAoVivoResponse;
import com.gomesdev.sortifyteams.domain.reserva.ReservaService;
import com.gomesdev.sortifyteams.domain.racha.sorteio.SorteioService;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RachaService {

    /**
     * Número do grupo "Goleiros" (sem numeração real na UI) usado quando os
     * goleiros ficam de fora do sorteio. Os times de verdade são numerados a
     * partir de 1, então 0 é seguro como sentinela — o app renderiza como
     * "Goleiros" em vez de "Time 0".
     */
    private static final int NUMERO_GRUPO_GOLEIROS = 0;

    private final RachaRepository rachaRepository;
    private final ParticipanteRachaRepository participanteRepository;
    private final TimeRachaRepository timeRepository;
    private final EsporteService esporteService;
    private final UsuarioRepository usuarioRepository;
    private final SorteioService sorteioService;
    private final ReservaService reservaService;
    private final RachaLocalizacaoService rachaLocalizacaoService;
    private final NotificacaoService notificacaoService;
    private final PartidaService partidaService;

    public RachaService(RachaRepository rachaRepository,
                        ParticipanteRachaRepository participanteRepository,
                        TimeRachaRepository timeRepository,
                        EsporteService esporteService,
                        UsuarioRepository usuarioRepository,
                        SorteioService sorteioService,
                        ReservaService reservaService,
                        RachaLocalizacaoService rachaLocalizacaoService,
                        NotificacaoService notificacaoService,
                        PartidaService partidaService) {
        this.rachaRepository = rachaRepository;
        this.participanteRepository = participanteRepository;
        this.timeRepository = timeRepository;
        this.esporteService = esporteService;
        this.usuarioRepository = usuarioRepository;
        this.sorteioService = sorteioService;
        this.reservaService = reservaService;
        this.rachaLocalizacaoService = rachaLocalizacaoService;
        this.notificacaoService = notificacaoService;
        this.partidaService = partidaService;
    }

    @Transactional
    public RachaResponse criar(RachaRequest request, Usuario organizador) {
        Esporte esporte = esporteService.buscarEntidade(request.esporteId());

        // limiteVagas menor que o mínimo do sorteio é permitido de propósito: há
        // rachas sem sorteio (o erro do sorteio já explica a conta quando faltar gente).
        Racha racha = new Racha(request, organizador.getId());
        // Só rachas públicos entram na busca por proximidade — só eles precisam de coordenadas.
        if (racha.isPublico()) {
            rachaLocalizacaoService.resolver(racha);
        }
        racha = rachaRepository.save(racha);

        if (Boolean.TRUE.equals(request.organizadorJoga())) {
            if (request.organizadorNivelTecnico() == null) {
                throw new IllegalArgumentException("Informe o nível técnico do organizador para incluí-lo no racha.");
            }
            participanteRepository.save(new ParticipanteRacha(racha.getId(), organizador.getId(),
                    request.organizadorNivelTecnico(), Boolean.TRUE.equals(request.organizadorGoleiro())));
        }

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

    /**
     * Feed de rachas públicos próximos. Com {@code lat/lon} ordena por distância;
     * sem GPS, exige {@code cidade} e filtra por ela (FR: obriga a filtrar por cidade).
     */
    @Transactional(readOnly = true)
    public List<RachaPublicoResponse> listarPublicos(Double lat, Double lon, String cidade,
                                                     Double raioKm, Usuario usuario) {
        boolean porGps = lat != null && lon != null;
        boolean porCidade = cidade != null && !cidade.isBlank();
        if (!porGps && !porCidade) {
            throw new IllegalArgumentException(
                    "Permita a localização ou escolha uma cidade para ver os rachas próximos.");
        }

        List<Racha> candidatos = rachaRepository.findPublicosAbertos(LocalDate.now());

        // Primeiro nome de cada organizador, em lote (privacidade: só o nome).
        Map<String, String> nomeOrganizador = new HashMap<>();
        usuarioRepository.findAllById(candidatos.stream().map(Racha::getOrganizadorId).distinct().toList())
                .forEach(u -> nomeOrganizador.put(u.getId(), u.getNomeCompleto().split(" ")[0]));
        Map<String, Esporte> esporteCache = new HashMap<>();

        record ComDistancia(Racha racha, Double distanciaKm) {}
        String cidadeAlvo = porCidade ? cidade.trim() : null;

        Comparator<ComDistancia> ordem = porGps
                ? Comparator.comparingDouble(cd -> cd.distanciaKm())
                : Comparator
                        .comparing((ComDistancia cd) -> cd.racha().getData(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(cd -> cd.racha().getHorario(),
                                Comparator.nullsLast(Comparator.naturalOrder()));

        return candidatos.stream()
                .filter(r -> porGps
                        ? (r.getLatitude() != null && r.getLongitude() != null)
                        : (r.getCidade() != null && r.getCidade().equalsIgnoreCase(cidadeAlvo)))
                .map(r -> new ComDistancia(r,
                        porGps ? distanciaKm(lat, lon, r.getLatitude(), r.getLongitude()) : null))
                .filter(cd -> !porGps || raioKm == null || cd.distanciaKm() <= raioKm)
                .sorted(ordem)
                .limit(50)
                .map(cd -> {
                    Racha r = cd.racha();
                    Esporte esporte = esporteCache.computeIfAbsent(r.getEsporteId(), esporteService::buscarEntidade);
                    long qtd = participanteRepository.countByRachaId(r.getId());
                    boolean souMembro = r.getOrganizadorId().equals(usuario.getId())
                            || participanteRepository.existsByRachaIdAndUsuarioId(r.getId(), usuario.getId());
                    return new RachaPublicoResponse(r.getId(), esporte.getNome(), esporte.getIcone(),
                            r.getData(), r.getHorario(), r.getLocal(), r.getCidade(), qtd, r.getLimiteVagas(),
                            nomeOrganizador.getOrDefault(r.getOrganizadorId(), ""),
                            cd.distanciaKm() != null ? Math.round(cd.distanciaKm() * 10) / 10.0 : null,
                            souMembro, r.getTokenConvite());
                })
                .toList();
    }

    /** Cidades distintas que têm rachas públicos abertos — alimenta o filtro por cidade. */
    @Transactional(readOnly = true)
    public List<String> listarCidadesPublicas() {
        return rachaRepository.findPublicosAbertos(LocalDate.now()).stream()
                .map(Racha::getCidade)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
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

    /**
     * Edita um racha aberto (tela "Editar racha"): local, público, nível técnico,
     * balanceamento, limite de vagas e quantidade de times. Campos nulos mantêm o
     * valor atual. Desligar o nível técnico também desliga o balanceamento. Os
     * níveis já informados dos jogadores são preservados (voltam se reativado).
     */
    @Transactional
    public RachaResponse atualizarConfiguracao(String rachaId, RachaConfigRequest request, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        garantirAberto(racha);

        String localAntes = racha.getLocal();
        boolean eraPublico = racha.isPublico();

        if (request.local() != null) {
            String local = request.local().isBlank() ? null : request.local().trim();
            racha.setLocal(local);
        }
        if (request.limiteVagas() != null) {
            racha.setLimiteVagas(request.limiteVagas());
        }
        if (request.qtdTimes() != null) {
            racha.setQtdTimes(request.qtdTimes());
        }
        if (request.incluirGoleirosNoSorteio() != null) {
            racha.setIncluirGoleirosNoSorteio(request.incluirGoleirosNoSorteio());
        }
        if (request.publico() != null) {
            racha.setPublico(request.publico());
        }

        if (request.criterioEmpateZero() != null) {
            racha.setCriterioEmpateZero(request.criterioEmpateZero());
        }
        if (request.criterioEmpateGols() != null) {
            racha.setCriterioEmpateGols(request.criterioEmpateGols());
        }

        boolean usaNivel = request.usaNivelTecnico() != null
                ? request.usaNivelTecnico() : racha.isUsaNivelTecnico();
        boolean balancear = request.balancearNivel() != null
                ? request.balancearNivel() : racha.isBalancearNivel();
        racha.setUsaNivelTecnico(usaNivel);
        racha.setBalancearNivel(usaNivel && balancear);

        // Re-resolve a localização só quando faz diferença (evita bater no Nominatim à toa).
        boolean localMudou = !java.util.Objects.equals(localAntes, racha.getLocal());
        boolean virouPublico = !eraPublico && racha.isPublico();
        if (racha.isPublico() && (localMudou || virouPublico || racha.getLatitude() == null)) {
            rachaLocalizacaoService.resolver(racha);
        } else if (eraPublico && !racha.isPublico()) {
            racha.setLatitude(null);
            racha.setLongitude(null);
            racha.setCidade(null);
        }

        rachaRepository.save(racha);
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

        boolean incluirGoleiros = racha.isIncluirGoleirosNoSorteio();
        List<SorteioService.JogadorSorteio> jogadores = participantes.stream()
                .map(p -> new SorteioService.JogadorSorteio(p.getId(), p.getNivelTecnico(),
                        esporte.isExigeGoleiro() && p.isEGoleiro()))
                .toList();

        List<SorteioService.TimeSorteado> sorteados = sorteioService.sortear(
                jogadores, racha.getQtdTimes(), racha.isBalancearNivel(),
                esporte.getJogadoresMinimosPorTime(), incluirGoleiros, new Random());

        // Re-sorteio: limpa vínculos e times anteriores. O flush garante que o
        // DELETE dos times antigos chegue ao banco ANTES dos INSERTs dos novos —
        // sem ele o Hibernate insere primeiro e colide na UNIQUE (racha_id, numero).
        participantes.forEach(p -> p.setTimeId(null));
        participanteRepository.saveAll(participantes);
        timeRepository.deleteByRachaId(rachaId);
        timeRepository.flush();

        Map<String, ParticipanteRacha> porId = participantes.stream()
                .collect(Collectors.toMap(ParticipanteRacha::getId, Function.identity()));
        for (SorteioService.TimeSorteado sorteado : sorteados) {
            TimeRacha time = timeRepository.save(new TimeRacha(rachaId, sorteado.numero()));
            sorteado.jogadores().forEach(j -> porId.get(j.participanteId()).setTimeId(time.getId()));
        }

        // Goleiros fora do sorteio: grupo à parte (sem número) com todos os goleiros.
        if (!incluirGoleiros) {
            List<ParticipanteRacha> goleiros = participantes.stream()
                    .filter(p -> esporte.isExigeGoleiro() && p.isEGoleiro())
                    .toList();
            if (!goleiros.isEmpty()) {
                TimeRacha grupoGoleiros = timeRepository.save(new TimeRacha(rachaId, NUMERO_GRUPO_GOLEIROS));
                goleiros.forEach(g -> g.setTimeId(grupoGoleiros.getId()));
            }
        }
        participanteRepository.saveAll(participantes);

        return montarResponse(racha);
    }

    /**
     * Ajuste manual dos times já sorteados (tela "Editar times"): o organizador
     * troca jogadores de time depois do sorteio. Reatribuição parcial — só os
     * participantes enviados mudam de time; os demais ficam onde estão.
     */
    @Transactional
    public RachaResponse atualizarTimes(String rachaId, TimesRequest request, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        garantirAberto(racha);

        List<TimeRacha> times = timeRepository.findByRachaIdOrderByNumero(rachaId);
        if (times.isEmpty()) {
            throw new IllegalArgumentException("Sorteie os times antes de editá-los.");
        }
        Map<Integer, String> timeIdPorNumero = times.stream()
                .collect(Collectors.toMap(TimeRacha::getNumero, TimeRacha::getId));

        Map<String, ParticipanteRacha> porId = participanteRepository.findByRachaId(rachaId).stream()
                .collect(Collectors.toMap(ParticipanteRacha::getId, Function.identity()));

        List<ParticipanteRacha> alterados = new ArrayList<>();
        for (TimesRequest.Atribuicao atribuicao : request.atribuicoes()) {
            ParticipanteRacha participante = porId.get(atribuicao.participanteId());
            if (participante == null) {
                throw new EntityNotFoundException(
                        "Participante não encontrado neste racha: " + atribuicao.participanteId());
            }
            String timeId = timeIdPorNumero.get(atribuicao.timeNumero());
            if (timeId == null) {
                throw new IllegalArgumentException(
                        "O time %d não existe neste racha.".formatted(atribuicao.timeNumero()));
            }
            participante.setTimeId(timeId);
            alterados.add(participante);
        }
        participanteRepository.saveAll(alterados);

        return montarResponse(racha);
    }

    /**
     * Inicia o racha ao vivo (EM_ANDAMENTO): congela times/config (as mutações
     * exigem ABERTO) e habilita partidas e gols. Só com times já sorteados.
     */
    @Transactional
    public RachaAoVivoResponse iniciar(String rachaId, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        garantirAberto(racha);

        long timesJogaveis = timeRepository.findByRachaIdOrderByNumero(rachaId).stream()
                .filter(t -> t.getNumero() != NUMERO_GRUPO_GOLEIROS)
                .count();
        if (timesJogaveis < 2) {
            throw new IllegalArgumentException("Sorteie os times antes de iniciar o racha.");
        }

        racha.setStatus(StatusRachaEnum.EM_ANDAMENTO);
        racha.setIniciadoEm(java.time.LocalDateTime.now());
        rachaRepository.save(racha);

        Esporte esporte = esporteService.buscarEntidade(racha.getEsporteId());
        String corpo = "O racha de %s começou — acompanhe o placar ao vivo!".formatted(esporte.getNome());
        participanteRepository.findByRachaId(racha.getId()).stream()
                .map(ParticipanteRacha::getUsuarioId)
                .filter(id -> id != null && !id.equals(racha.getOrganizadorId()))
                .distinct()
                .forEach(usuarioId -> notificacaoService.notificar(
                        usuarioId, "RACHA_AO_VIVO", "Racha ao vivo", corpo));

        return partidaService.broadcast(racha);
    }

    /** Conclui o racha, registrando a duração do cronômetro (C1). Alimenta contadores e dashboard (C14). */
    @Transactional
    public RachaResponse concluir(String rachaId, ConcluirRachaRequest request, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        boolean estavaAoVivo = racha.getStatus() == StatusRachaEnum.EM_ANDAMENTO;
        if (racha.getStatus() != StatusRachaEnum.ABERTO && !estavaAoVivo) {
            throw new IllegalArgumentException(
                    "O racha não está mais aberto (status: %s).".formatted(racha.getStatus()));
        }
        // Um racha ao vivo está acontecendo agora — a data marcada não importa mais.
        if (!estavaAoVivo && racha.getData() != null && racha.getData().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Não é possível concluir um racha marcado para o futuro (%s). Cancele-o se ele não vai acontecer."
                            .formatted(racha.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        }

        if (estavaAoVivo) {
            partidaService.encerrarPartidaAtivaSeExistir(rachaId);
        }
        racha.setStatus(StatusRachaEnum.CONCLUIDO);
        if (request != null && request.duracaoPartidaSeg() != null) {
            racha.setDuracaoPartidaSeg(request.duracaoPartidaSeg());
        }
        rachaRepository.save(racha);
        if (estavaAoVivo) {
            partidaService.broadcast(racha);
        }
        return montarResponse(racha);
    }

    /** Cancela o racha e, em cascata, a reserva confirmada — notificando o dono e os participantes (C10). */
    @Transactional
    public void cancelar(String rachaId, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        garantirOrganizador(racha, usuario);
        garantirAberto(racha);

        racha.setStatus(StatusRachaEnum.CANCELADO);
        rachaRepository.save(racha);
        reservaService.cancelarPorCancelamentoDoRacha(rachaId);

        String corpo = "O organizador cancelou o racha%s."
                .formatted(racha.getData() != null
                        ? " de " + racha.getData().format(DateTimeFormatter.ofPattern("dd/MM"))
                        : "");
        participanteRepository.findByRachaId(racha.getId()).stream()
                .map(ParticipanteRacha::getUsuarioId)
                .filter(id -> id != null && !id.equals(racha.getOrganizadorId()))
                .distinct()
                .forEach(usuarioId -> notificacaoService.notificar(
                        usuarioId, "RACHA_CANCELADO", "Racha cancelado", corpo));
    }

    // ---------- convite (C9) ----------

    @Transactional(readOnly = true)
    public ConviteResponse detalharConvite(String token, Usuario usuario) {
        Racha racha = buscarPorToken(token);
        var esporte = esporteService.buscarEntidade(racha.getEsporteId());
        Usuario organizador = usuarioRepository.findById(racha.getOrganizadorId()).orElseThrow();
        long qtd = participanteRepository.countByRachaId(racha.getId());
        boolean jaParticipa = racha.getOrganizadorId().equals(usuario.getId())
                || participanteRepository.existsByRachaIdAndUsuarioId(racha.getId(), usuario.getId());
        // FR-016: só o primeiro nome do organizador
        String primeiroNome = organizador.getNomeCompleto().split(" ")[0];
        return new ConviteResponse(racha.getId(), esporte.getNome(), esporte.getIcone(),
                esporte.isExigeGoleiro(), racha.isUsaNivelTecnico(), primeiroNome, racha.getData(),
                racha.getHorario(), racha.getLocal(), qtd, racha.getLimiteVagas(), racha.getStatus(), jaParticipa);
    }

    /** Preview público (sem autenticação) para a landing web do convite. */
    @Transactional(readOnly = true)
    public ConvitePublicoView previewConvite(String token) {
        Racha racha = buscarPorToken(token);
        var esporte = esporteService.buscarEntidade(racha.getEsporteId());
        Usuario organizador = usuarioRepository.findById(racha.getOrganizadorId()).orElseThrow();
        long qtd = participanteRepository.countByRachaId(racha.getId());
        String primeiroNome = organizador.getNomeCompleto().split(" ")[0];
        String quando = "";
        if (racha.getData() != null) {
            quando = racha.getData().format(DateTimeFormatter.ofPattern("dd/MM"));
            if (racha.getHorario() != null) {
                quando += " às " + racha.getHorario().format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        }
        return new ConvitePublicoView(esporte.getNome(), esporte.getIcone(), primeiroNome,
                quando, racha.getLocal(), qtd, racha.getLimiteVagas(), racha.getStatus() == StatusRachaEnum.ABERTO);
    }

    @Transactional
    public RachaResponse entrarPorConvite(String token, EntrarConviteRequest request, Usuario usuario) {
        Racha racha = buscarPorToken(token);
        garantirAberto(racha);
        if (racha.getOrganizadorId().equals(usuario.getId())
                || participanteRepository.existsByRachaIdAndUsuarioId(racha.getId(), usuario.getId())) {
            throw new IllegalArgumentException("Você já está neste racha.");
        }
        if (racha.getLimiteVagas() != null
                && participanteRepository.countByRachaId(racha.getId()) >= racha.getLimiteVagas()) {
            throw new IllegalArgumentException("O racha já atingiu o limite de vagas.");
        }
        participanteRepository.save(new ParticipanteRacha(racha.getId(), usuario.getId(),
                request.nivelTecnico(), Boolean.TRUE.equals(request.eGoleiro())));
        return montarResponse(racha);
    }

    /** Participante sai do racha (Fluxo 5). O organizador cancela em vez de sair. */
    @Transactional
    public void sairDoRacha(String rachaId, Usuario usuario) {
        Racha racha = buscarEntidade(rachaId);
        if (racha.getOrganizadorId().equals(usuario.getId())) {
            throw new IllegalArgumentException("O organizador não sai do racha — cancele-o.");
        }
        // Gols ao vivo referenciam participantes — sair no meio quebraria o histórico.
        if (racha.getStatus() == StatusRachaEnum.EM_ANDAMENTO) {
            throw new IllegalArgumentException("O racha está em andamento — não é possível sair agora.");
        }
        ParticipanteRacha participante = participanteRepository
                .findByRachaIdAndUsuarioId(rachaId, usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException("Você não participa deste racha."));
        participanteRepository.delete(participante);
    }

    private Racha buscarPorToken(String token) {
        return rachaRepository.findByTokenConvite(token)
                .orElseThrow(() -> new EntityNotFoundException("Convite inválido."));
    }

    // ---------- privados ----------

    /** Distância em km entre dois pontos (fórmula de Haversine). */
    private static double distanciaKm(double lat1, double lon1, double lat2, double lon2) {
        double raioTerra = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return raioTerra * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

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
