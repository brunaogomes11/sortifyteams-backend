package com.gomesdev.sortifyteams.domain.versaoapp.response;

import com.gomesdev.sortifyteams.enums.SituacaoAtualizacaoEnum;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta da checagem de atualização (spec 002, FR-001/FR-012).
 *
 * <p><b>Contrato tolerante a cliente antigo</b>: campos novos só podem ser
 * acrescentados, nunca renomeados ou removidos — é justamente o app
 * desatualizado que precisa conseguir ler esta resposta para se atualizar.
 * Por isso {@code obrigatoria} vem explícito em vez de o app ter que derivá-lo
 * do enum: um cliente que não conheça um valor novo de {@code situacao} ainda
 * entende se está bloqueado.
 */
@Schema(description = "O que há de novo para o runtime informado")
public record AtualizacaoResponse(

        @Schema(description = "Classificação da checagem (FR-002)")
        SituacaoAtualizacaoEnum situacao,

        @Schema(description = "true quando o cliente está abaixo do mínimo suportado (C4)")
        boolean obrigatoria,

        @Schema(description = "APK disponível; nulo quando não há runtime novo")
        RuntimeDisponivelResponse runtime,

        @Schema(description = "versionCode mínimo suportado pela versão publicada")
        Integer versionCodeMinimo
) {

    public static AtualizacaoResponse emDia() {
        return new AtualizacaoResponse(SituacaoAtualizacaoEnum.EM_DIA, false, null, null);
    }
}
