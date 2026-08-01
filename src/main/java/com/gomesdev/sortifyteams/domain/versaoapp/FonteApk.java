package com.gomesdev.sortifyteams.domain.versaoapp;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * De onde o APK vem ao ser publicado. Existe para que a publicação valha tanto
 * para o upload do painel quanto para a importação do arquivo legado (T006),
 * sem duplicar validação, hash e gravação.
 *
 * <p>O contrato exige que {@link #abrir()} possa ser chamado mais de uma vez —
 * a publicação lê o conteúdo duas vezes (hashes e gravação).
 */
public interface FonteApk {

    String nome();

    boolean vazio();

    InputStream abrir() throws IOException;

    static FonteApk de(MultipartFile arquivo) {
        return new FonteApk() {
            @Override
            public String nome() {
                return arquivo.getOriginalFilename();
            }

            @Override
            public boolean vazio() {
                return arquivo == null || arquivo.isEmpty();
            }

            @Override
            public InputStream abrir() throws IOException {
                return arquivo.getInputStream();
            }
        };
    }

    static FonteApk de(Resource recurso, String nome) {
        return new FonteApk() {
            @Override
            public String nome() {
                return nome;
            }

            @Override
            public boolean vazio() {
                try {
                    return !recurso.exists() || recurso.contentLength() == 0;
                } catch (IOException e) {
                    return true;
                }
            }

            @Override
            public InputStream abrir() throws IOException {
                return recurso.getInputStream();
            }
        };
    }
}
