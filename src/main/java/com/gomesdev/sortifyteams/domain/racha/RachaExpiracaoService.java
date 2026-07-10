package com.gomesdev.sortifyteams.domain.racha;

import com.gomesdev.sortifyteams.domain.notificacao.NotificacaoService;
import com.gomesdev.sortifyteams.domain.racha.partida.PartidaService;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Expira rachas abertos cuja data já passou (T-teste manual: racha sem
 * conclusão ficava aberto para sempre). Vira CANCELADO — não CONCLUIDO, para
 * não inflar os contadores de "rachas jogados" do perfil (FR-012).
 */
@Service
public class RachaExpiracaoService {

    private static final Logger log = LoggerFactory.getLogger(RachaExpiracaoService.class);

    private final RachaRepository rachaRepository;
    private final NotificacaoService notificacaoService;
    private final PartidaService partidaService;

    public RachaExpiracaoService(RachaRepository rachaRepository, NotificacaoService notificacaoService,
                                 PartidaService partidaService) {
        this.rachaRepository = rachaRepository;
        this.notificacaoService = notificacaoService;
        this.partidaService = partidaService;
    }

    /** Roda todo dia às 3h15 (configurável via app.racha.expiracao-cron). */
    @Scheduled(cron = "${app.racha.expiracao-cron:0 15 3 * * *}")
    public void expiracaoDiaria() {
        int expirados = expirarRachasVencidos();
        if (expirados > 0) {
            log.info("Expiração diária de rachas: {} rachas cancelados por vencimento.", expirados);
        }
        int concluidos = concluirEmAndamentoEsquecidos();
        if (concluidos > 0) {
            log.info("Expiração diária de rachas: {} rachas ao vivo concluídos automaticamente.", concluidos);
        }
    }

    /**
     * Racha que entrou ao vivo e ficou esquecido (organizador não concluiu):
     * após 24h vira CONCLUIDO — diferente do ABERTO vencido (CANCELADO), pois o
     * jogo de fato aconteceu e deve contar no perfil (FR-012).
     */
    @Transactional
    public int concluirEmAndamentoEsquecidos() {
        List<Racha> esquecidos = rachaRepository.findByStatusAndIniciadoEmBefore(
                StatusRachaEnum.EM_ANDAMENTO, LocalDateTime.now().minusHours(24));
        for (Racha racha : esquecidos) {
            partidaService.encerrarPartidaAtivaSeExistir(racha.getId());
            racha.setStatus(StatusRachaEnum.CONCLUIDO);
            rachaRepository.save(racha);
            notificacaoService.notificar(racha.getOrganizadorId(), "RACHA_CONCLUIDO_AUTO",
                    "Racha concluído",
                    "Seu racha ao vivo ficou aberto por mais de 24h e foi concluído automaticamente.");
            partidaService.broadcast(racha);
        }
        return esquecidos.size();
    }

    @Transactional
    public int expirarRachasVencidos() {
        List<Racha> vencidos = rachaRepository.findByStatusAndDataBefore(StatusRachaEnum.ABERTO, LocalDate.now());
        for (Racha racha : vencidos) {
            racha.setStatus(StatusRachaEnum.CANCELADO);
            rachaRepository.save(racha);
            notificacaoService.notificar(racha.getOrganizadorId(), "RACHA_EXPIRADO",
                    "Racha expirado",
                    "Seu racha de %s não foi concluído e expirou.".formatted(racha.getData()));
        }
        return vencidos.size();
    }
}
