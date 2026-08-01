package com.gomesdev.sortifyteams.config.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Validação de upload de imagem (perfil e fotos de quadra).
 *
 * <p>Existe por causa da spec 002: o limite de multipart
 * ({@code spring.servlet.multipart.max-file-size}) é <b>global</b> e precisou
 * subir para caber o APK (~30 MB). Antes disso ele era, na prática, o único
 * teto de tamanho das imagens — sem esta checagem, subir o limite global
 * passaria a permitir que qualquer usuário autenticado enviasse uma "foto" de
 * 150 MB. O APK não passa por aqui: vai direto para o banco, por outro caminho.
 */
public final class UploadImagem {

    /** Teto que o multipart global garantia antes da spec 002. */
    public static final long TAMANHO_MAXIMO_BYTES = 10L * 1024 * 1024;

    private UploadImagem() {
    }

    public static void validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Envie um arquivo de imagem.");
        }
        String contentType = arquivo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Envie um arquivo de imagem.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new IllegalArgumentException(
                    "A imagem passa do limite de %d MB.".formatted(TAMANHO_MAXIMO_BYTES / (1024 * 1024)));
        }
    }
}
