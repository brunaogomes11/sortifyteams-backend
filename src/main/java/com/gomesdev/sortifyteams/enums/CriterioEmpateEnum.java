package com.gomesdev.sortifyteams.enums;

/**
 * Critério de desempate de uma partida do racha ao vivo (dinâmica
 * "vencedor fica"). Cada racha configura um critério para empate 0x0 e outro
 * para empate com gols (1x1, 2x2...), pois as regras de casa variam.
 */
public enum CriterioEmpateEnum {
    /** O time que já estava em quadra (veio da partida anterior) leva. */
    TIME_QUE_FICA,
    /** O time desafiante (que acabou de entrar) leva. */
    TIME_QUE_ENTRA,
    /** Ninguém leva: os dois saem e entram os próximos da fila. */
    AMBOS_SAEM,
    /** Próximo gol decide — a partida segue até desempatar. */
    GOL_DE_OURO,
    /** Disputa de pênaltis decide; o vencedor é informado no encerramento. */
    PENALTIS
}
