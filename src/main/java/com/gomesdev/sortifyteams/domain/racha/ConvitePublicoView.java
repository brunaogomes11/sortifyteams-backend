package com.gomesdev.sortifyteams.domain.racha;

/**
 * Dados do racha exibidos na landing web do convite (fallback quando o app não
 * está instalado). Sem dados sensíveis — só o suficiente para o convidado
 * decidir entrar. Datas já vêm formatadas para exibição direta no template.
 */
public record ConvitePublicoView(
        String esporteNome,
        String esporteIcone,
        String organizador,
        String quando,
        String local,
        long qtdParticipantes,
        Integer limiteVagas,
        boolean aberto
) {
}
