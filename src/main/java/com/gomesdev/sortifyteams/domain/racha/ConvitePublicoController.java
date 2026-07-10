package com.gomesdev.sortifyteams.domain.racha;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Página web do convite (C9). O link compartilhado é uma URL http normal
 * (GET /convite/{token}); ao abrir, a página de convite tenta o deep link
 * racha://convite/&lt;token&gt; (app instalado) e mostra o preview do racha com CTA
 * para instalar o app. Token inexistente cai numa página própria de convite
 * inválido. Rota pública (ver SecurityConfig).
 *
 * A landing page do produto (marketing) é separada e não passa por aqui.
 */
@Controller
public class ConvitePublicoController {

    private final RachaService rachaService;
    private final String deepLinkBase;

    public ConvitePublicoController(RachaService rachaService,
                                    @Value("${app.deep-link-base}") String deepLinkBase) {
        this.rachaService = rachaService;
        this.deepLinkBase = deepLinkBase;
    }

    @GetMapping("/convite/{token}")
    public String convite(@PathVariable String token, Model model) {
        try {
            model.addAttribute("racha", rachaService.previewConvite(token));
            model.addAttribute("deepLink", deepLinkBase + "/" + token);
            return "convite/convite";
        } catch (EntityNotFoundException e) {
            return "convite/invalido";
        }
    }
}
