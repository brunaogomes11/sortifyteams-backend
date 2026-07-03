package com.gomesdev.sortifyteams.config.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Resolve a URL base pública da aplicação para montar links de arquivos/recursos.
 * Prioriza o valor configurado em {@code app.base-url}, garantindo o esquema
 * correto mesmo atrás de um proxy que termina o TLS. Quando não configurado,
 * cai para a detecção a partir da requisição corrente.
 */
@Component
public class BaseUrlResolver {

    private final String configuredBaseUrl;

    public BaseUrlResolver(@Value("${app.base-url:}") String configuredBaseUrl) {
        this.configuredBaseUrl = configuredBaseUrl;
    }

    public String resolve() {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return stripTrailingSlash(configuredBaseUrl.trim());
        }
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (IllegalStateException ex) {
            return "http://localhost:8080";
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
