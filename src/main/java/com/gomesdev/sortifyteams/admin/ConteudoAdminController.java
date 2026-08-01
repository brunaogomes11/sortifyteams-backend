package com.gomesdev.sortifyteams.admin;

import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.versaoapp.conteudo.PacoteConteudo;
import com.gomesdev.sortifyteams.domain.versaoapp.conteudo.PacoteConteudoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Publicação de pacotes de conteúdo (spec 002, T021). O painel deixa explícito
 * qual pacote está ativo em cada runtime — publicar no runtime errado é o erro
 * mais fácil de cometer aqui, e o que não pode passar despercebido (FR-005).
 */
@Controller
@RequestMapping("/admin/conteudo")
public class ConteudoAdminController {

    private final PacoteConteudoService service;

    public ConteudoAdminController(PacoteConteudoService service) {
        this.service = service;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("pacotes", service.listar());
        return "admin/conteudo";
    }

    @PostMapping
    public String publicar(@RequestParam String runtimeVersion,
                           @RequestParam(required = false) String notas,
                           @RequestParam MultipartFile arquivo,
                           @AuthenticationPrincipal Usuario admin,
                           RedirectAttributes redirect) {
        try {
            PacoteConteudo pacote = service.publicar(arquivo, runtimeVersion, notas,
                    admin != null ? admin.getId() : null);
            redirect.addFlashAttribute("mensagem",
                    "Pacote publicado e ativo para o runtime %s.".formatted(pacote.getRuntimeVersion()));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/conteudo";
    }

    @PostMapping("/{id}/ativar")
    public String ativar(@PathVariable String id, RedirectAttributes redirect) {
        PacoteConteudo pacote = service.ativar(id);
        redirect.addFlashAttribute("mensagem",
                "Pacote de %s voltou a ser o ativo do runtime %s."
                        .formatted(pacote.getPublicadoEm(), pacote.getRuntimeVersion()));
        return "redirect:/admin/conteudo";
    }

    @PostMapping("/{id}/despublicar")
    public String despublicar(@PathVariable String id, RedirectAttributes redirect) {
        PacoteConteudo pacote = service.despublicar(id);
        redirect.addFlashAttribute("mensagem",
                "Pacote despublicado — o runtime %s volta ao bundle embutido no APK."
                        .formatted(pacote.getRuntimeVersion()));
        return "redirect:/admin/conteudo";
    }
}
