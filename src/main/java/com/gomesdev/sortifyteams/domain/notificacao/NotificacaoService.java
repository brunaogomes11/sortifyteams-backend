package com.gomesdev.sortifyteams.domain.notificacao;

import com.gomesdev.sortifyteams.domain.notificacao.push.ExpoPushClient;
import com.gomesdev.sortifyteams.domain.notificacao.push.PushToken;
import com.gomesdev.sortifyteams.domain.notificacao.push.PushTokenRepository;
import com.gomesdev.sortifyteams.domain.notificacao.response.NotificacaoResponse;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacaoService {

    private final NotificacaoRepository repository;
    private final PushTokenRepository pushTokenRepository;
    private final ExpoPushClient expoPushClient;

    public NotificacaoService(NotificacaoRepository repository,
                              PushTokenRepository pushTokenRepository,
                              ExpoPushClient expoPushClient) {
        this.repository = repository;
        this.pushTokenRepository = pushTokenRepository;
        this.expoPushClient = expoPushClient;
    }

    /** Cria a notificação in-app e dispara push (melhor-esforço). */
    @Transactional
    public void notificar(String usuarioId, String tipo, String titulo, String corpo) {
        repository.save(new Notificacao(usuarioId, tipo, titulo, corpo));
        List<String> tokens = pushTokenRepository.findByUsuarioId(usuarioId).stream()
                .map(PushToken::getExpoToken)
                .toList();
        expoPushClient.enviar(tokens, titulo, corpo);
    }

    @Transactional
    public void registrarPushToken(Usuario usuario, String expoToken, String plataforma) {
        pushTokenRepository.findByUsuarioIdAndExpoToken(usuario.getId(), expoToken)
                .orElseGet(() -> pushTokenRepository.save(
                        new PushToken(usuario.getId(), expoToken, plataforma)));
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listar(Usuario usuario) {
        return repository.findTop50ByUsuarioIdOrderByCriadaEmDesc(usuario.getId()).stream()
                .map(NotificacaoResponse::new)
                .toList();
    }

    @Transactional
    public void marcarLida(String id, Usuario usuario) {
        Notificacao notificacao = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notificação não encontrada: " + id));
        if (!notificacao.getUsuarioId().equals(usuario.getId())) {
            throw new AccessDeniedException("Notificação de outro usuário.");
        }
        notificacao.setLida(true);
        repository.save(notificacao);
    }
}
