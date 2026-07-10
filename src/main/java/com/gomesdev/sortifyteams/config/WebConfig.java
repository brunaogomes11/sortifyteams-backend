package com.gomesdev.sortifyteams.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoggingInterceptor loggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor);
    }

    /**
     * O produto é PT-BR. Sem isto, as mensagens de bean validation seguem o
     * Accept-Language do cliente (celular em inglês receberia erros em inglês).
     */
    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.of("pt", "BR"));
    }
}
