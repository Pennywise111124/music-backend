package com.example.musicbackend.storage;

import lombok.Getter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Getter
public enum FileCategory {

    AUDIO("audio",
            Set.of("mp3", "m4a", "wav", "ogg"),
            Set.of("audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/m4a",
                    "audio/wav", "audio/x-wav", "audio/wave", "audio/ogg", "application/ogg"),
            20L * 1024 * 1024),

    IMAGE("images",
            Set.of("jpg", "jpeg", "png", "webp"),
            Set.of("image/jpeg", "image/png", "image/webp"),
            5L * 1024 * 1024);

    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "mp3", "audio/mpeg", "m4a", "audio/mp4", "wav", "audio/wav", "ogg", "audio/ogg",
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final String folder;
    private final Set<String> allowedExtensions;
    private final Set<String> allowedContentTypes;
    private final long maxBytes;

    FileCategory(String folder, Set<String> exts, Set<String> types, long maxBytes) {
        this.folder = folder;
        this.allowedExtensions = exts;
        this.allowedContentTypes = types;
        this.maxBytes = maxBytes;
    }

    public static Optional<FileCategory> fromFolder(String folder) {
        for (FileCategory c : values()) {
            if (c.folder.equals(folder)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public static String mediaTypeFor(String ext) {
        return ext == null ? "application/octet-stream"
                : MEDIA_TYPES.getOrDefault(ext.toLowerCase(), "application/octet-stream");
    }
}