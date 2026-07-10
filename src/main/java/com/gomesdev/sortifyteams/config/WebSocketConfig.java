package com.gomesdev.sortifyteams.config;

import com.gomesdev.sortifyteams.security.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal ao vivo do racha (placar/gols em tempo real). Endpoint STOMP em /ws —
 * fora de /api/** de propósito: a cadeia stateless JWT não se aplica a
 * WebSocket; a autenticação acontece no frame CONNECT
 * ({@link StompAuthChannelInterceptor}). Broker simples em memória com um
 * tópico por racha: /topic/rachas/{id}, payload = RachaAoVivoResponse.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Apps mobile não enviam um Origin confiável — a proteção real é o JWT do CONNECT.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Scheduler criado à mão (NÃO é bean de propósito: um TaskScheduler no
        // contexto vira candidato a executor de bootstrap do JPA e fecha um
        // ciclo entityManagerFactory → WebSocketConfig → repositórios → JPA).
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();

        // Heartbeat 10s/10s: detecta sessões mortas de clientes mobile
        // (React Native congela timers em background e some sem FIN).
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(scheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
