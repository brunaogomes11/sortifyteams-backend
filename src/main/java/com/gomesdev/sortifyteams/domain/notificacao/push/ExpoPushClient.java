package com.gomesdev.sortifyteams.domain.notificacao.push;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente do Expo Push Service (D5). O app registra o ExponentPushToken no
 * login e o backend dispara o push por HTTP. Melhor-esforço: falha de push
 * nunca derruba a operação de negócio — a tabela tb_notificacao é a fonte
 * de verdade.
 */
@Component
public class ExpoPushClient {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);

    private final RestClient restClient;

    public ExpoPushClient(@Value("${app.expo.push-url:https://exp.host/--/api/v2/push/send}") String pushUrl) {
        this.restClient = RestClient.builder().baseUrl(pushUrl).build();
    }

    public void enviar(List<String> expoTokens, String titulo, String corpo) {
        if (expoTokens.isEmpty()) {
            return;
        }
        try {
            List<Map<String, Object>> mensagens = expoTokens.stream()
                    .map(token -> Map.<String, Object>of(
                            "to", token,
                            "title", titulo,
                            "body", corpo != null ? corpo : "",
                            "sound", "default"))
                    .toList();
            restClient.post()
                    .body(mensagens)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Falha ao enviar push Expo (seguindo sem push): {}", e.getMessage());
        }
    }
}
