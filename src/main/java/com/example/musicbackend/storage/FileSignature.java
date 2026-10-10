package com.example.musicbackend.storage;

import java.util.Optional;

final class FileSignature {

    static final int HEADER_BYTES = 16;

    private FileSignature() {
    }

    static Optional<String> detect(FileCategory category, byte[] header) {
        if (category == null || header == null) {
            return Optional.empty();
        }
        return switch (category) {
            case IMAGE -> detectImage(header);
            case AUDIO -> detectAudio(header);
            default -> Optional.empty();
        };
    }

    private static Optional<String> detectImage(byte[] h) {
        if (matches(h, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("jpg");
        }
        if (matches(h, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("png");
        }
        // WebP và WAV cùng mở đầu "RIFF", phân biệt bằng byte 8..11
        if (matchesAscii(h, 0, "RIFF") && matchesAscii(h, 8, "WEBP")) {
            return Optional.of("webp");
        }
        return Optional.empty();
    }

    private static Optional<String> detectAudio(byte[] h) {
        if (matchesAscii(h, 0, "ID3") || isMpegFrame(h)) {
            return Optional.of("mp3");
        }
        if (matchesAscii(h, 4, "ftyp")) {
            return Optional.of("m4a");
        }
        if (matchesAscii(h, 0, "RIFF") && matchesAscii(h, 8, "WAVE")) {
            return Optional.of("wav");
        }
        if (matchesAscii(h, 0, "OggS")) {
            return Optional.of("ogg");
        }
        return Optional.empty();
    }

    /** Khung MPEG: byte[0] = FF và 3 bit cao của byte[1] đều bằng 1. */
    private static boolean isMpegFrame(byte[] h) {
        return h.length >= 2 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0;
    }

    /** So sánh từng byte (đã bỏ dấu bằng & 0xFF), kiểm tra độ dài trước khi đọc. */
    private static boolean matches(byte[] h, int offset, int... expected) {
        if (h.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((h[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAscii(byte[] h, int offset, String text) {
        if (h.length < offset + text.length()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if ((h[offset + i] & 0xFF) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}