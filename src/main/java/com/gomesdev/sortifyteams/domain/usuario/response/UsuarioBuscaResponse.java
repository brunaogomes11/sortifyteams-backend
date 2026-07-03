package com.gomesdev.sortifyteams.domain.usuario.response;

import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado mínimo da busca de usuários (privacidade — FR-016)")
public record UsuarioBuscaResponse(
        @Schema(description = "ID ULID do usuário") String id,
        @Schema(description = "Nome de usuário") String username,
        @Schema(description = "Nome completo") String nomeCompleto,
        @Schema(description = "URL da foto de perfil") String fotoPerfil
) {
    public UsuarioBuscaResponse(Usuario usuario) {
        this(usuario.getId(), usuario.getUsername(), usuario.getNomeCompleto(), usuario.getFotoPerfil());
    }
}
