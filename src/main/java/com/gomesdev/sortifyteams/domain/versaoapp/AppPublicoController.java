package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.domain.versaoapp.response.AtualizacaoResponse;
import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Rotas públicas do app (spec 002). Ficam sem autenticação de propósito: a
 * checagem e o download acontecem <b>antes do login</b>, e um cliente com
 * versão incompatível precisa conseguir se atualizar mesmo sem sessão válida.
 * Nenhum dado de usuário trafega aqui (Constituição III).
 */
@RestController
@RequestMapping("/api/app")
@Tag(name = "App", description = "Versão e download do app (público)")
public class AppPublicoController {

    private final VersaoAppService versaoService;
    private final ApkStreamService streamService;
    private final AtualizacaoService atualizacaoService;

    public AppPublicoController(VersaoAppService versaoService, ApkStreamService streamService,
                                AtualizacaoService atualizacaoService) {
        this.versaoService = versaoService;
        this.streamService = streamService;
        this.atualizacaoService = atualizacaoService;
    }

    @GetMapping("/atualizacao")
    @Operation(summary = "O que há de novo para o runtime informado (checagem de abertura)")
    public ResponseEntity<AtualizacaoResponse> checar(
            @RequestParam(defaultValue = "ANDROID") PlataformaAppEnum plataforma,
            @RequestParam(required = false) String runtimeVersion,
            @RequestParam(required = false) Integer versionCode) {
        return ResponseEntity.ok(atualizacaoService.checar(plataforma, runtimeVersion, versionCode));
    }

    @GetMapping("/apk")
    @Operation(summary = "Baixa o APK da versão ativa (aceita Range para retomada)")
    public ResponseEntity<StreamingResponseBody> baixarAtiva(
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange) {
        VersaoRuntime versao = versaoService.ativa(PlataformaAppEnum.ANDROID)
                .orElseThrow(() -> new EntityNotFoundException("Nenhuma versão do app publicada."));
        return streamService.servir(versao, range, ifRange);
    }

    @GetMapping("/apk/{versionCode}")
    @Operation(summary = "Baixa o APK de uma versão específica (aceita Range)")
    public ResponseEntity<StreamingResponseBody> baixarVersao(
            @PathVariable int versionCode,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange) {
        VersaoRuntime versao = versaoService.porVersionCode(PlataformaAppEnum.ANDROID, versionCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Versão não encontrada: versionCode " + versionCode));
        return streamService.servir(versao, range, ifRange);
    }
}
