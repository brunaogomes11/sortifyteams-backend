package com.gomesdev.sortifyteams.config.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final String basePath;
    private final BaseUrlResolver baseUrlResolver;

    public LocalStorageService(@Value("${app.storage.local-path:uploads}") String basePath,
                               BaseUrlResolver baseUrlResolver) {
        this.basePath = basePath;
        this.baseUrlResolver = baseUrlResolver;
    }

    @Override
    public String store(MultipartFile file, String prefix) throws IOException {
        String extension = extractExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + extension;
        String key = prefix + "/" + filename;

        Path target = Paths.get(basePath).resolve(key).normalize();
        Files.createDirectories(target.getParent());
        Files.copy(file.getInputStream(), target);

        return key;
    }

    @Override
    public void delete(String key) {
        Path target = Paths.get(basePath).resolve(key).normalize();
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getUrl(String key) {
        return baseUrlResolver.resolve() + "/files/" + key;
    }

    @Override
    public void writeTo(String key, OutputStream outputStream) throws IOException {
        Path target = Paths.get(basePath).resolve(key).normalize();
        if (!target.startsWith(Paths.get(basePath).normalize()) || !Files.exists(target)) {
            throw new IOException("Arquivo nao encontrado: " + key);
        }
        Files.copy(target, outputStream);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return originalFilename.substring(dotIndex);
    }
}
