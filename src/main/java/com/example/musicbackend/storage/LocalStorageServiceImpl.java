package com.example.musicbackend.storage;

import com.example.musicbackend.exception.FileTooLargeException;
import com.example.musicbackend.exception.InvalidFileException;
import com.example.musicbackend.exception.StorageException;
import com.example.musicbackend.exception.StoredFileNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LocalStorageServiceImpl implements StorageService {

    private static final String URL_PREFIX = "/api/files/";

    private static final Pattern STORED_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]{2,4}$");

    private final Path root;

    public LocalStorageServiceImpl(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        for (FileCategory c : FileCategory.values()) {
            Files.createDirectories(root.resolve(c.getFolder()));
        }
        log.info("Upload directory: {}", root);
    }

    @Override
    public String store(MultipartFile file, FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }

        if (file.getSize() > category.getMaxBytes()) {
            throw new FileTooLargeException(category.getMaxBytes());
        }

        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null) {
            throw new InvalidFileException("File extension is missing");
        }
        ext = ext.toLowerCase(Locale.ROOT);
        if (!category.getAllowedExtensions().contains(ext)) {
            throw new InvalidFileException("File extension is not allowed: " + ext);
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new InvalidFileException("Content-Type is missing");
        }
        contentType = contentType.toLowerCase(Locale.ROOT);
        if (contentType.contains(";")) {
            contentType = contentType.split(";")[0].trim();
        }
        if (!category.getAllowedContentTypes().contains(contentType)) {
            throw new InvalidFileException("Content-Type is not allowed: " + contentType);
        }

        byte[] header = readHeader(file);
        String detected = FileSignature.detect(category, header)
                .orElseThrow(() -> new InvalidFileException(
                        "File content is not a valid " + kindOf(category) + " file"));

        String declared = ext.equals("jpeg") ? "jpg" : ext;
        if (!detected.equals(declared)) {
            throw new InvalidFileException("File content does not match its extension");
        }

        String filename = UUID.randomUUID() + "." + ext;

        Path dir = root.resolve(category.getFolder());
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir)) {
            throw new InvalidFileException("Invalid file path");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        } catch (IOException e) {
            throw new StorageException("Could not store file", e);
        }

        return URL_PREFIX + category.getFolder() + "/" + filename;
    }

    @Override
    public Resource load(FileCategory category, String filename) {
        if (filename == null || !STORED_NAME.matcher(filename).matches()) {
            throw new StoredFileNotFoundException();
        }

        Path dir = root.resolve(category.getFolder());
        Path path = dir.resolve(filename).normalize();
        if (!path.startsWith(dir)) {
            throw new StoredFileNotFoundException();
        }

        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new StoredFileNotFoundException();
        }

        return new FileSystemResource(path);
    }

    @Override
    public void delete(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(URL_PREFIX)) {
            return;
        }
        String[] parts = publicUrl.substring(URL_PREFIX.length()).split("/");
        if (parts.length != 2 || !STORED_NAME.matcher(parts[1]).matches()
                || FileCategory.fromFolder(parts[0]).isEmpty()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(parts[0]).resolve(parts[1]));
        } catch (IOException e) {
            log.warn("Could not delete file {}", publicUrl, e);
        }
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] buf = new byte[FileSignature.HEADER_BYTES];
            int n = in.readNBytes(buf, 0, buf.length);
            return Arrays.copyOf(buf, n);
        } catch (IOException e) {
            throw new StorageException("Could not read file", e);
        }
    }

    private static String kindOf(FileCategory category) {
        return category == FileCategory.AUDIO ? "audio" : "image";
    }
}