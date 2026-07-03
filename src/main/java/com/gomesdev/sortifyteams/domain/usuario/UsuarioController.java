package com.gomesdev.sortifyteams.domain.usuario;

import com.gomesdev.sortifyteams.domain.usuario.response.UsuarioBuscaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários", description = "Consulta de usuários")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping("/busca")
    @Operation(summary = "Busca usuários por username ou nome",
            description = "Usada pelo organizador para convidar usuários cadastrados a um racha. Retorna no máximo 20 resultados com dados mínimos.")
    public ResponseEntity<List<UsuarioBuscaResponse>> buscar(@RequestParam("q") String query) {
        return ResponseEntity.ok(service.buscar(query));
    }
}
