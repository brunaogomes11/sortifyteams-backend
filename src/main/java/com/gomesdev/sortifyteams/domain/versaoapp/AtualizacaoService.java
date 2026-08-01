package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.config.storage.BaseUrlResolver;
import com.gomesdev.sortifyteams.domain.versaoapp.response.AtualizacaoResponse;
import com.gomesdev.sortifyteams.domain.versaoapp.response.RuntimeDisponivelResponse;
import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import com.gomesdev.sortifyteams.enums.SituacaoAtualizacaoEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Checagem de atualização (spec 002, FR-001/FR-002).
 *
 * <p>A classificação é regra crítica: é ela que decide entre deixar o usuário
 * entrar, baixar conteúdo ou bloquear o app. Fica isolada em
 * {@link #classificar} — função pura, sem banco — para poder ser testada nos
 * limites (Constituição IV).
 */
@Service
public class AtualizacaoService {

    private final VersaoRuntimeRepository versaoRepository;
    private final BaseUrlResolver baseUrlResolver;

    public AtualizacaoService(VersaoRuntimeRepository versaoRepository,
                              BaseUrlResolver baseUrlResolver) {
        this.versaoRepository = versaoRepository;
        this.baseUrlResolver = baseUrlResolver;
    }

    @Transactional(readOnly = true)
    public AtualizacaoResponse checar(PlataformaAppEnum plataforma, String runtimeVersion,
                                      Integer versionCode) {
        Optional<VersaoRuntime> ativa = versaoRepository.findByPlataformaAndAtivaTrue(plataforma);
        if (ativa.isEmpty()) {
            return AtualizacaoResponse.emDia();
        }
        VersaoRuntime publicada = ativa.get();

        // Cliente que não informou a própria versão não tem como ser comparado;
        // devolver "em dia" é mais seguro que bloquear por falta de dado.
        if (versionCode == null) {
            return AtualizacaoResponse.emDia();
        }

        // Camada de conteúdo entra na Fase 5 (T020) — até lá, nunca há
        // conteúdo novo a oferecer.
        boolean conteudoNovo = false;

        SituacaoAtualizacaoEnum situacao = classificar(versionCode, runtimeVersion,
                publicada.getVersionCode(), publicada.getVersionCodeMinimo(),
                publicada.getRuntimeVersion(), conteudoNovo);

        boolean obrigatoria = situacao == SituacaoAtualizacaoEnum.RUNTIME_OBRIGATORIO;
        RuntimeDisponivelResponse runtime = situacao == SituacaoAtualizacaoEnum.EM_DIA
                ? null : montarRuntime(publicada);

        return new AtualizacaoResponse(situacao, obrigatoria, runtime,
                publicada.getVersionCodeMinimo());
    }

    /**
     * Regra de decisão (FR-002). Pura de propósito.
     *
     * <p>Ordem importa: o bloqueio por mínimo suportado vem <b>antes</b> de
     * qualquer outra coisa — um cliente incompatível com a API não deve ser
     * mandado para uma atualização de conteúdo que não vai resolver o problema
     * dele.
     *
     * @param conteudoNovoDisponivel se há pacote de conteúdo mais novo para o
     *                               runtime do cliente (preenchido na Fase 5)
     */
    static SituacaoAtualizacaoEnum classificar(int versionCodeInstalado,
                                               String runtimeInstalado,
                                               int versionCodePublicado,
                                               int versionCodeMinimo,
                                               String runtimePublicado,
                                               boolean conteudoNovoDisponivel) {
        if (versionCodeInstalado < versionCodeMinimo) {
            return SituacaoAtualizacaoEnum.RUNTIME_OBRIGATORIO;
        }
        if (versionCodeInstalado < versionCodePublicado) {
            return SituacaoAtualizacaoEnum.RUNTIME_OPCIONAL;
        }
        // Daqui para baixo o cliente está no runtime publicado (ou à frente).
        // Conteúdo só se aplica quando o runtime bate (C13) — se o app instalado
        // tem runtime diferente, OTA não resolve e não há APK novo a oferecer.
        if (conteudoNovoDisponivel && runtimeCompativel(runtimeInstalado, runtimePublicado)) {
            return SituacaoAtualizacaoEnum.CONTEUDO;
        }
        return SituacaoAtualizacaoEnum.EM_DIA;
    }

    private static boolean runtimeCompativel(String instalado, String publicado) {
        return instalado != null && instalado.equals(publicado);
    }

    private RuntimeDisponivelResponse montarRuntime(VersaoRuntime versao) {
        String url = baseUrlResolver.resolve() + "/api/app/apk/" + versao.getVersionCode();
        return new RuntimeDisponivelResponse(versao.getVersao(), versao.getVersionCode(), url,
                versao.getTamanhoBytes(), versao.getSha256(), versao.getMd5(),
                versao.getNotas(), versao.getPublicadaEm());
    }
}
