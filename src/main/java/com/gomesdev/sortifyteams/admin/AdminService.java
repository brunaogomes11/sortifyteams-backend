package com.gomesdev.sortifyteams.admin;

import com.gomesdev.sortifyteams.domain.notificacao.NotificacaoService;
import com.gomesdev.sortifyteams.domain.quadra.Quadra;
import com.gomesdev.sortifyteams.domain.quadra.QuadraRepository;
import com.gomesdev.sortifyteams.domain.racha.RachaRepository;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioRepository;
import com.gomesdev.sortifyteams.enums.RoleEnum;
import com.gomesdev.sortifyteams.enums.StatusUsuarioEnum;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    /** Linha do dashboard C14: rachas concluídos por quadra. */
    public record DashboardItem(String quadraNome, String quadraEndereco, long totalRachas) {}

    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;
    private final RachaRepository rachaRepository;
    private final QuadraRepository quadraRepository;

    public AdminService(UsuarioRepository usuarioRepository,
                        NotificacaoService notificacaoService,
                        RachaRepository rachaRepository,
                        QuadraRepository quadraRepository) {
        this.usuarioRepository = usuarioRepository;
        this.notificacaoService = notificacaoService;
        this.rachaRepository = rachaRepository;
        this.quadraRepository = quadraRepository;
    }

    /** Dashboard (C14): quantidade de rachas concluídos por quadra, expansível. */
    @Transactional(readOnly = true)
    public List<DashboardItem> dashboard() {
        return rachaRepository.concluidosPorQuadra().stream()
                .map(linha -> {
                    String quadraId = (String) linha[0];
                    long total = (Long) linha[1];
                    Quadra quadra = quadraRepository.findById(quadraId).orElse(null);
                    return new DashboardItem(
                            quadra != null ? quadra.getNome() : "(quadra removida)",
                            quadra != null ? quadra.getEndereco() : "",
                            total);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Usuario> listarSolicitacoesPendentes() {
        return usuarioRepository.findByRoleAndStatusOrderByCriadoEmAsc(
                RoleEnum.DONO_QUADRA, StatusUsuarioEnum.PENDENTE);
    }

    @Transactional
    public void aprovar(String usuarioId) {
        Usuario dono = buscarDonoPendente(usuarioId);
        dono.setStatus(StatusUsuarioEnum.APROVADO);
        usuarioRepository.save(dono);
        notificacaoService.notificar(dono.getId(), "DONO_APROVADO",
                "Cadastro aprovado!",
                "Sua conta de Dono de Quadra foi aprovada. Faça login para gerenciar suas quadras.");
    }

    @Transactional
    public void rejeitar(String usuarioId) {
        Usuario dono = buscarDonoPendente(usuarioId);
        dono.setStatus(StatusUsuarioEnum.REJEITADO);
        usuarioRepository.save(dono);
        notificacaoService.notificar(dono.getId(), "DONO_REJEITADO",
                "Cadastro rejeitado",
                "Sua solicitação de Dono de Quadra foi rejeitada. Você pode reenviá-la pelo app.");
    }

    private Usuario buscarDonoPendente(String usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + usuarioId));
        if (usuario.getRole() != RoleEnum.DONO_QUADRA || usuario.getStatus() != StatusUsuarioEnum.PENDENTE) {
            throw new IllegalArgumentException("A solicitação não está pendente.");
        }
        return usuario;
    }
}
