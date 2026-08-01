package com.gomesdev.sortifyteams.enums;

/**
 * Plataforma de distribuição do app (spec 002, C1). Só ANDROID no escopo
 * atual — iOS não permite instalar fora da loja. O enum existe desde já para
 * que a chave única de versão seja (plataforma, version_code) e um cliente
 * iOS futuro não exija migração de dados.
 */
public enum PlataformaAppEnum {
    ANDROID
}
