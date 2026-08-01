package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Expurgo dos binários antigos (spec 002, C23/FR-030).
 *
 * <p><b>Cron diário, não {@code fixedDelay} de 30 dias</b>: a contagem do
 * {@code fixedDelay} reinicia a cada restart da aplicação, então com deploys
 * frequentes o job simplesmente nunca dispararia. O cron roda todo dia e a
 * idade é filtrada na consulta — mesmo efeito, previsível e idempotente.
 *
 * <p>Política: preserva sempre o binário da versão <b>ativa</b> e da
 * <b>imediatamente anterior</b>. A anterior fica porque é o alvo do "voltar
 * atrás" (C14) — sem ela, um rollback depois de 30 dias exigiria rebuildar o
 * APK. Metadados nunca são apagados: a versão vira histórico, não some.
 */
@Service
public class VersaoExpurgoService {

    private static final Logger log = LoggerFactory.getLogger(VersaoExpurgoService.class);

    private final VersaoRuntimeRepository versaoRepository;
    private final VersaoRuntimeArquivoRepository arquivoRepository;
    private final ApkBinarioRepository binarioRepository;
    private final int diasDeRetencao;

    public VersaoExpurgoService(VersaoRuntimeRepository versaoRepository,
                                VersaoRuntimeArquivoRepository arquivoRepository,
                                ApkBinarioRepository binarioRepository,
                                @Value("${app.versao.retencao-dias:30}") int diasDeRetencao) {
        this.versaoRepository = versaoRepository;
        this.arquivoRepository = arquivoRepository;
        this.binarioRepository = binarioRepository;
        this.diasDeRetencao = diasDeRetencao;
    }

    @Scheduled(cron = "${app.versao.expurgo-cron:0 40 3 * * *}")
    public void expurgoDiario() {
        for (PlataformaAppEnum plataforma : PlataformaAppEnum.values()) {
            int removidos = expurgar(plataforma, LocalDateTime.now().minusDays(diasDeRetencao));
            if (removidos > 0) {
                log.info("Expurgo de versões {}: {} binário(s) removido(s), metadados preservados.",
                        plataforma, removidos);
            }
        }
    }

    /**
     * Executa a política e devolve quantos binários foram removidos.
     * Exposto para teste e para acionamento manual.
     */
    @Transactional
    public int expurgar(PlataformaAppEnum plataforma, LocalDateTime limite) {
        List<VersaoRuntime> versoes =
                versaoRepository.findByPlataformaOrderByVersionCodeDesc(plataforma);
        if (versoes.isEmpty()) {
            return 0;
        }

        List<String> preservados = idsPreservados(versoes);
        int removidos = 0;

        for (VersaoRuntime versao : versoes) {
            if (preservados.contains(versao.getId())) {
                continue;
            }
            if (versao.isSomenteHistorico()) {
                continue; // já expurgada
            }
            if (versao.getPublicadaEm() != null && versao.getPublicadaEm().isAfter(limite)) {
                continue; // ainda dentro da janela de retenção
            }
            binarioRepository.apagarBinario(versao.getId());
            arquivoRepository.deleteByVersaoRuntimeId(versao.getId());
            versao.setBinarioExpurgadoEm(LocalDateTime.now());
            versaoRepository.save(versao);
            removidos++;
        }
        return removidos;
    }

    /**
     * Ativa + imediatamente anterior. "Anterior" é a de maior versionCode entre
     * as que não são a ativa — não necessariamente a penúltima publicada, já que
     * um rollback pode ter deixado a ativa com versionCode menor.
     */
    private List<String> idsPreservados(List<VersaoRuntime> versoesDesc) {
        VersaoRuntime ativa = versoesDesc.stream()
                .filter(VersaoRuntime::isAtiva)
                .findFirst()
                .orElse(versoesDesc.get(0));

        VersaoRuntime anterior = versoesDesc.stream()
                .filter(v -> !v.getId().equals(ativa.getId()))
                .filter(v -> !v.isSomenteHistorico())
                .findFirst()
                .orElse(null);

        return anterior == null
                ? List.of(ativa.getId())
                : List.of(ativa.getId(), anterior.getId());
    }
}
