package com.gomesdev.sortifyteams.domain.versaoapp;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Compatibilidade do caminho antigo de download (spec 002, C24/FR-032).
 *
 * <p>A URL {@code /downloads/sortify-teams-v1.0.0.apk} já foi distribuída — em
 * links, na landing e na página de convite. Depois que o APK saiu do JAR, ela
 * não pode virar 404 nem continuar entregando um arquivo velho: passa a
 * redirecionar para o download da versão ativa.
 */
@Controller
public class DownloadLegadoController {

    @GetMapping("/downloads/**")
    public String redirecionarParaVersaoAtiva() {
        return "redirect:/api/app/apk";
    }
}
