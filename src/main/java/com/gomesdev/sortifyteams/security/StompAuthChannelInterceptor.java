package com.gomesdev.sortifyteams.security;

import com.gomesdev.sortifyteams.domain.racha.Racha;
import com.gomesdev.sortifyteams.domain.racha.RachaRepository;
import com.gomesdev.sortifyteams.domain.racha.ParticipanteRachaRepository;
import com.gomesdev.sortifyteams.domain.usuario.Usuario;
import com.gomesdev.sortifyteams.domain.usuario.UsuarioService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autenticação/autorização do canal STOMP (o handshake HTTP de /ws é liberado
 * no SecurityConfig — a auth real acontece aqui):
 * - CONNECT: exige JWT válido no header "Authorization" do frame (mesmo token
 *   dos endpoints REST). O usuário fica associado à sessão WS.
 * - SUBSCRIBE: só membros do racha (organizador ou participante) podem assinar
 *   /topic/rachas/{id}.
 * Qualquer exceção aqui vira um frame ERROR e derruba a conexão.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TOPICO_RACHA = Pattern.compile("^/topic/rachas/([0-9A-HJKMNP-TV-Z]{26})$");

    private final JwtService jwtService;
    private final UsuarioService usuarioService;
    private final RachaRepository rachaRepository;
    private final ParticipanteRachaRepository participanteRepository;

    public StompAuthChannelInterceptor(JwtService jwtService,
                                       UsuarioService usuarioService,
                                       RachaRepository rachaRepository,
                                       ParticipanteRachaRepository participanteRepository) {
        this.jwtService = jwtService;
        this.usuarioService = usuarioService;
        this.rachaRepository = rachaRepository;
        this.participanteRepository = participanteRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            autenticar(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            autorizarSubscribe(accessor);
        }
        return message;
    }

    private void autenticar(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Token de acesso ausente no CONNECT.");
        }
        String jwt = header.substring(7);
        String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            throw new AccessDeniedException("Token de acesso inválido.");
        }
        UserDetails userDetails = usuarioService.loadUserByUsername(username);
        if (!jwtService.isTokenValid(jwt, userDetails)) {
            throw new AccessDeniedException("Token de acesso expirado ou inválido.");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()));
    }

    private void autorizarSubscribe(StompHeaderAccessor accessor) {
        String destino = accessor.getDestination();
        Matcher matcher = destino != null ? TOPICO_RACHA.matcher(destino) : null;
        if (matcher == null || !matcher.matches()) {
            throw new AccessDeniedException("Destino de assinatura não permitido.");
        }
        Usuario usuario = usuarioDaSessao(accessor);
        String rachaId = matcher.group(1);
        Racha racha = rachaRepository.findById(rachaId)
                .orElseThrow(() -> new EntityNotFoundException("Racha não encontrado: " + rachaId));
        boolean organizador = racha.getOrganizadorId().equals(usuario.getId());
        boolean participante = participanteRepository.existsByRachaIdAndUsuarioId(rachaId, usuario.getId());
        if (!organizador && !participante) {
            throw new AccessDeniedException("Você não participa deste racha.");
        }
    }

    private Usuario usuarioDaSessao(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }
        throw new AccessDeniedException("Sessão não autenticada.");
    }
}
