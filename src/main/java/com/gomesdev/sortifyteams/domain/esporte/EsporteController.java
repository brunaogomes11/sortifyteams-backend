package com.gomesdev.sortifyteams.domain.esporte;

import com.gomesdev.sortifyteams.domain.esporte.response.EsporteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/esportes")
@Tag(name = "Esportes", description = "Catálogo de esportes do app")
public class EsporteController {

    private final EsporteService service;

    public EsporteController(EsporteService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista os esportes disponíveis",
            description = "Grid de esportes da tela Criar Racha, com configuração de goleiro e mínimos por time.")
    public ResponseEntity<List<EsporteResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }
}
