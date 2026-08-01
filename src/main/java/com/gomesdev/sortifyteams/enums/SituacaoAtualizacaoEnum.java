package com.gomesdev.sortifyteams.enums;

/**
 * Resultado da checagem de atualização (spec 002, FR-002). O app decide o que
 * fazer na tela de abertura a partir daqui.
 */
public enum SituacaoAtualizacaoEnum {

    /** Nada a baixar — a tela de abertura sai e o app entra (C17). */
    EM_DIA,

    /** Pacote de conteúdo novo para o mesmo runtime: baixa sem instalador. */
    CONTEUDO,

    /** APK novo disponível, mas o instalado ainda é compatível — adiável (C8). */
    RUNTIME_OPCIONAL,

    /** Instalado abaixo do mínimo suportado: bloqueia até atualizar (C4). */
    RUNTIME_OBRIGATORIO
}
