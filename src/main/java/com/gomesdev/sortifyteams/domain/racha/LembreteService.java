package com.gomesdev.sortifyteams.domain.racha;

import com.gomesdev.sortifyteams.domain.notificacao.NotificacaoService;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lembrete de racha (T040): avisa organizador e jogadores no dia do jogo. */
@Service
public class LembreteService {

    private static final Logger log = LoggerFactory.getLogger(LembreteService.class);

    private final RachaRepository rachaRepository;
    private final ParticipanteRachaRepository participanteRepository;
    private final NotificacaoService notificacaoService;

    public LembreteService(RachaRepository rachaRepository,
                           ParticipanteRachaRepository participanteRepository,
                           NotificacaoService notificacaoService) {
        this.rachaRepository = rachaRepository;
        this.participanteRepository = participanteRepository;
        this.notificacaoService = notificacaoService;
    }

    /** Roda todo dia às 8h (configurável via app.lembrete.cron). */
    @Scheduled(cron = "${app.lembrete.cron:0 0 8 * * *}")
    public void lembreteDiario() {
        int avisados = lembrarRachasDeHoje();
        if (avisados > 0) {
            log.info("Lembrete diário: {} usuários avisados.", avisados);
        }
    }

    @Transactional
    public int lembrarRachasDeHoje() {
        List<Racha> rachasDeHoje = rachaRepository.findByDataAndStatus(LocalDate.now(), StatusRachaEnum.ABERTO);
        int avisados = 0;
        for (Racha racha : rachasDeHoje) {
            Set<String> usuarios = new HashSet<>();
            usuarios.add(racha.getOrganizadorId());
            participanteRepository.findByRachaId(racha.getId()).stream()
                    .map(ParticipanteRacha::getUsuarioId)
                    .filter(id -> id != null)
                    .forEach(usuarios::add);
            String horario = racha.getHorario() != null ? " às " + racha.getHorario() : "";
            for (String usuarioId : usuarios) {
                notificacaoService.notificar(usuarioId, "LEMBRETE_RACHA",
                        "Hoje tem racha!",
                        "Seu racha é hoje%s. Bora!".formatted(horario));
                avisados++;
            }
        }
        return avisados;
    }
}
