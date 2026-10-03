package com.example.musicbackend.controller;

import com.example.musicbackend.exception.StoredFileNotFoundException;
import com.example.musicbackend.storage.FileCategory;
import com.example.musicbackend.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;

    @GetMapping("/{type}/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String type, @PathVariable String filename) {
        FileCategory category = FileCategory.fromFolder(type)
                .orElseThrow(StoredFileNotFoundException::new);
        Resource resource = storageService.load(category, filename);

        String ext = StringUtils.getFilenameExtension(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(FileCategory.mediaTypeFor(ext)))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}