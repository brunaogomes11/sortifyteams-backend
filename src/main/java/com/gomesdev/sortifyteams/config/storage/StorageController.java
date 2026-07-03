package com.gomesdev.sortifyteams.config.storage;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class StorageController {

    private final String basePath;

    public StorageController(@Value("${app.storage.local-path:uploads}") String basePath) {
        this.basePath = basePath;
    }

    @GetMapping("/files/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String key = fullPath.substring("/files/".length());

        Path target = Paths.get(basePath).resolve(key).normalize();
        if (!target.startsWith(Paths.get(basePath).normalize()) || !Files.exists(target)) {
            throw new EntityNotFoundException("Arquivo não encontrado: " + key);
        }

        Resource resource = toResource(target);
        MediaType contentType = resolveContentType(target);

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + target.getFileName() + "\"")
                .body(resource);
    }

    private Resource toResource(Path target) {
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException e) {
            throw new EntityNotFoundException("Arquivo não encontrado: " + target.getFileName());
        }
    }

    private MediaType resolveContentType(Path target) {
        try {
            String detected = Files.probeContentType(target);
            if (detected != null) {
                return MediaType.parseMediaType(detected);
            }
        } catch (IOException ignored) {
            // fall through to default
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
