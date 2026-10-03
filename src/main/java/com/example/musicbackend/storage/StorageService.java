package com.example.musicbackend.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String store(MultipartFile file, FileCategory category);

    Resource load(FileCategory category, String filename);

    void delete(String publicUrl);
}