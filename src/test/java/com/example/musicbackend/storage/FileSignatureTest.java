package com.example.musicbackend.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.example.musicbackend.storage.FileCategory.AUDIO;
import static com.example.musicbackend.storage.FileCategory.IMAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FileSignatureTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    private static final byte[] PAD = new byte[8];

    private static final byte[] JPEG = concat(bytes(0xFF, 0xD8, 0xFF, 0xE0), PAD);
    private static final byte[] PNG = concat(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), PAD);
    private static final byte[] WEBP = concat(ascii("RIFF"), bytes(0, 0, 0, 0), ascii("WEBP"));
    private static final byte[] MP3_ID3 = concat(ascii("ID3"), bytes(3, 0, 0, 0, 0, 0, 0), PAD);
    private static final byte[] MP3_FRAME = concat(bytes(0xFF, 0xFB, 0x90, 0x00), PAD);
    private static final byte[] M4A = concat(bytes(0, 0, 0, 0x18), ascii("ftyp"), ascii("M4A "));
    private static final byte[] WAV = concat(ascii("RIFF"), bytes(0, 0, 0, 0), ascii("WAVE"));
    private static final byte[] OGG = concat(ascii("OggS"), PAD);

    // ---------- Nhận đúng ----------

    @Test
    void detectsImages() {
        assertEquals(Optional.of("jpg"), FileSignature.detect(IMAGE, JPEG));
        assertEquals(Optional.of("png"), FileSignature.detect(IMAGE, PNG));
        assertEquals(Optional.of("webp"), FileSignature.detect(IMAGE, WEBP));
    }

    @Test
    void detectsAudio() {
        assertEquals(Optional.of("mp3"), FileSignature.detect(AUDIO, MP3_ID3));
        assertEquals(Optional.of("mp3"), FileSignature.detect(AUDIO, MP3_FRAME));
        assertEquals(Optional.of("m4a"), FileSignature.detect(AUDIO, M4A));
        assertEquals(Optional.of("wav"), FileSignature.detect(AUDIO, WAV));
        assertEquals(Optional.of("ogg"), FileSignature.detect(AUDIO, OGG));
    }

    // ---------- Sai category ----------

    @Test
    void wrongCategory_isRejected() {
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, PNG));
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, JPEG));
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, WEBP));
        assertEquals(Optional.empty(), FileSignature.detect(IMAGE, MP3_ID3));
        assertEquals(Optional.empty(), FileSignature.detect(IMAGE, WAV));
    }

    @Test
    void riffContainers_areToldApartByBytes8To11() {
        assertEquals(Optional.of("webp"), FileSignature.detect(IMAGE, WEBP));
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, WEBP));
        assertEquals(Optional.of("wav"), FileSignature.detect(AUDIO, WAV));
        assertEquals(Optional.empty(), FileSignature.detect(IMAGE, WAV));

        byte[] avi = concat(ascii("RIFF"), bytes(0, 0, 0, 0), ascii("AVI "));
        assertEquals(Optional.empty(), FileSignature.detect(IMAGE, avi));
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, avi));
    }

    // ---------- Đầu vào xấu ----------

    @Test
    void tooShortOrEmptyOrGarbage_isRejected() {
        assertEquals(Optional.empty(), FileSignature.detect(IMAGE, new byte[0]));
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, new byte[0]));
        assertEquals(Optional.empty(), FileSignature.detect(IMAGE, bytes(0x89, 0x50))); // PNG cụt
        assertEquals(Optional.empty(), FileSignature.detect(IMAGE, bytes(0xFF, 0xD8))); // JPEG cụt (cần 3 byte)
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, bytes(0x49, 0x44))); // "ID" thiếu "3"
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, bytes(0xFF))); // thiếu byte thứ hai
        assertEquals(Optional.empty(), FileSignature.detect(IMAGE, ascii("RIFF"))); // thiếu 8..11
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, ascii("hello")));
        assertEquals(Optional.empty(), FileSignature.detect(IMAGE, ascii("hello")));
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, new byte[16])); // toàn số 0
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, null));
    }

    @Test
    void mpegFrame_needsAllThreeSyncBits() {
        assertEquals(Optional.empty(), FileSignature.detect(AUDIO, bytes(0xFF, 0xC0, 0, 0))); // chỉ 2 bit
        assertEquals(Optional.of("mp3"), FileSignature.detect(AUDIO, bytes(0xFF, 0xE0, 0, 0)));
    }
}