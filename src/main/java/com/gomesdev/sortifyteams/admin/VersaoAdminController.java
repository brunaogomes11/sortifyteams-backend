package com.gomesdev.sortifyteams.admin;

import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.versaoapp.ApkBinarioRepository;
import com.gomesdev.sortifyteams.domain.versaoapp.VersaoAppService;
import com.gomesdev.sortifyteams.domain.versaoapp.VersaoRuntime;
import com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest;
import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publicação de versões do app pelo painel (spec 002, Fluxo 7 / FR-018).
 * Protegido pela cadeia /admin/** (sessão + CSRF), como o resto do painel.
 */
@Controller
@RequestMapping("/admin/versoes")
public class VersaoAdminController {

    private final VersaoAppService service;
    private final ApkBinarioRepository binarioRepository;

    public VersaoAdminController(VersaoAppService service, ApkBinarioRepository binarioRepository) {
        this.service = service;
        this.binarioRepository = binarioRepository;
    }

    @GetMapping
    public String listar(Model model) {
        List<VersaoRuntime> versoes = service.listar(PlataformaAppEnum.ANDROID);
        // FR-031: o painel precisa deixar explícito quem ainda tem binário —
        // é o que decide se "voltar atrás" é oferecido.
        Map<String, Boolean> temBinario = new LinkedHashMap<>();
        versoes.forEach(v -> temBinario.put(v.getId(), binarioRepository.existeBinario(v.getId())));

        model.addAttribute("versoes", versoes);
        model.addAttribute("temBinario", temBinario);
        return "admin/versoes";
    }

    @PostMapping
    public String publicar(@RequestParam Integer versionCodeMinimo,
                           @RequestParam(required = false) String notas,
                           @RequestParam MultipartFile arquivo,
                           @AuthenticationPrincipal Usuario admin,
                           RedirectAttributes redirect) {
        try {
            // versao/versionCode/runtimeVersion vem do AndroidManifest.xml do
            // proprio arquivo (ApkManifestReader) — nao sao digitados aqui.
            var request = new PublicarVersaoRequest(versionCodeMinimo, notas);
            VersaoRuntime publicada = service.publicar(request, arquivo, PlataformaAppEnum.ANDROID,
                    admin != null ? admin.getId() : null);
            redirect.addFlashAttribute("mensagem",
                    "Versão %s (versionCode %d, runtime %s) lida do APK, publicada e ativa."
                            .formatted(publicada.getVersao(), publicada.getVersionCode(),
                                    publicada.getRuntimeVersion()));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/versoes";
    }

    @PostMapping("/{id}/ativar")
    public String ativar(@PathVariable String id, RedirectAttributes redirect) {
        try {
            VersaoRuntime versao = service.ativar(id);
            redirect.addFlashAttribute("mensagem",
                    "Versão %s voltou a ser a ativa.".formatted(versao.getVersao()));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/versoes";
    }

    @PostMapping("/{id}/despublicar")
    public String despublicar(@PathVariable String id, RedirectAttributes redirect) {
        VersaoRuntime versao = service.despublicar(id);
        redirect.addFlashAttribute("mensagem",
                "Versão %s despublicada — nenhuma versão ativa até ativar outra."
                        .formatted(versao.getVersao()));
        return "redirect:/admin/versoes";
    }
}
