package com.gomesdev.sortifyteams.domain.reserva;

/** Conflito de reserva (C8): o slot pedido já foi confirmado por outra reserva. */
public class ConflitoHorarioException extends RuntimeException {

    public ConflitoHorarioException(String message) {
        super(message);
    }
}
