package com.gomesdev.sortifyteams.domain.usuario.response;

import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.enums.RoleEnum;
import com.gomesdev.sortifyteams.enums.StatusUsuarioEnum;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados do usuário autenticado")
public record UsuarioResponse(
        @Schema(description = "ID ULID do usuário") String id,
        @Schema(description = "Nome completo") String nomeCompleto,
        @Schema(description = "Nome de usuário") String username,
        @Schema(description = "E-mail") String email,
        @Schema(description = "Papel") RoleEnum role,
        @Schema(description = "Status de aprovação") StatusUsuarioEnum status,
        @Schema(description = "URL da foto de perfil") String fotoPerfil,
        @Schema(description = "Contato") String contato
) {
    public UsuarioResponse(Usuario usuario) {
        this(usuario.getId(), usuario.getNomeCompleto(), usuario.getUsername(), usuario.getEmail(),
                usuario.getRole(), usuario.getStatus(), usuario.getFotoPerfil(), usuario.getContato());
    }
}
