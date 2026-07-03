package com.gomesdev.sortifyteams.domain.auth.response;

import com.gomesdev.sortifyteams.domain.usuario.Usuario;

/** Par de tokens emitido internamente pelo AuthService. */
public record AuthTokens(String accessToken, String refreshToken, Usuario usuario) {
}
