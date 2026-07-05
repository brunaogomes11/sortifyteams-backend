package com.gomesdev.sortifyteams.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Painel administrativo (Fluxo 8) — Thymeleaf + sessão, fora da API mobile. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping("/solicitacoes")
    public String solicitacoes(Model model) {
        model.addAttribute("solicitacoes", adminService.listarSolicitacoesPendentes());
        return "admin/solicitacoes";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("itens", adminService.dashboard());
        return "admin/dashboard";
    }

    @PostMapping("/solicitacoes/{id}/aprovar")
    public String aprovar(@PathVariable String id, RedirectAttributes redirect) {
        adminService.aprovar(id);
        redirect.addFlashAttribute("mensagem", "Solicitação aprovada.");
        return "redirect:/admin/solicitacoes";
    }

    @PostMapping("/solicitacoes/{id}/rejeitar")
    public String rejeitar(@PathVariable String id, RedirectAttributes redirect) {
        adminService.rejeitar(id);
        redirect.addFlashAttribute("mensagem", "Solicitação rejeitada.");
        return "redirect:/admin/solicitacoes";
    }
}
