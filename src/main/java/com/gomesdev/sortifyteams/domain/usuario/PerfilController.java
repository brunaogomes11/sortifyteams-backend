package com.gomesdev.sortifyteams.domain.usuario;

import com.gomesdev.sortifyteams.domain.usuario.request.PerfilRequest;
import com.gomesdev.sortifyteams.domain.usuario.response.PerfilResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/perfil")
@Tag(name = "Perfil", description = "Perfil do usuário logado (Fluxo 6)")
public class PerfilController {

    private final PerfilService service;

    public PerfilController(PerfilService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Perfil com contador de rachas e esporte preferido (C11)")
    public ResponseEntity<PerfilResponse> perfil(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.montar(usuario));
    }

    @PutMapping
    @Operation(summary = "Atualiza nome, contato e override do esporte preferido")
    public ResponseEntity<PerfilResponse> atualizar(@Valid @RequestBody PerfilRequest request,
                                                    @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.atualizar(request, usuario));
    }

    @PutMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Atualiza a foto de perfil")
    public ResponseEntity<PerfilResponse> atualizarFoto(@RequestPart("arquivo") MultipartFile arquivo,
                                                        @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(service.atualizarFoto(arquivo, usuario));
    }
}
