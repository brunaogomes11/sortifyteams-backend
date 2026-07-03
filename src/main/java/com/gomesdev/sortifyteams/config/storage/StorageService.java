package com.gomesdev.sortifyteams.config.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;

public interface StorageService {
    String store(MultipartFile file, String prefix) throws IOException;
    void delete(String key);
    String getUrl(String key);
    void writeTo(String key, OutputStream outputStream) throws IOException;
}
