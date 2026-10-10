package com.example.musicbackend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "app.storage.upload-dir=target/test-uploads"
})
abstract class IntegrationTestBase {

    static final String PASSWORD = "Passw0rd!";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        MusicBackendApplication.configureTimeZone();
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    int port;

    @Value("${app.storage.upload-dir}")
    String uploadDir;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    // ---------- HTTP ----------

    record Resp(int status, String body) {
        String str(String path) {
            return JsonPath.read(body, path);
        }

        long num(String path) {
            return ((Number) JsonPath.read(body, path)).longValue();
        }

        List<String> list(String path) {
            return JsonPath.read(body, path);
        }

        <T> T read(String path) {
            return JsonPath.read(body, path);
        }
    }

    Resp call(String method, String path, String token, String json) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            if (json != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(json));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(r.statusCode(), r.body());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Resp get(String path, String token) {
        return call("GET", path, token, null);
    }

    Resp post(String path, String token, String json) {
        return call("POST", path, token, json);
    }

    Resp put(String path, String token, String json) {
        return call("PUT", path, token, json);
    }

    Resp delete(String path, String token) {
        return call("DELETE", path, token, null);
    }

    // ---------- Người dùng ----------

    record TestUser(long id, String username, String access, String refresh) {
    }

    String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    Resp registerRaw(String username) {
        return post("/api/auth/register", null, """
                {"username":"%s","email":"%s@example.com","password":"%s"}
                """.formatted(username, username, PASSWORD));
    }

    Resp loginRaw(String usernameOrEmail, String password) {
        return post("/api/auth/login", null, """
                {"usernameOrEmail":"%s","password":"%s"}
                """.formatted(usernameOrEmail, password));
    }

    TestUser newUser() {
        String username = unique("u");
        Resp r = registerRaw(username);
        if (r.status() != 201) {
            throw new IllegalStateException("register failed: " + r.status() + " " + r.body());
        }
        return new TestUser(r.num("$.user.id"), username, r.str("$.accessToken"), r.str("$.refreshToken"));
    }

    // Quyền đọc từ DB ở mỗi request nên đổi role bằng SQL là có hiệu lực ngay,
    // không cần login lại
    TestUser newAdmin() {
        TestUser u = newUser();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", u.id());
        return u;
    }

    // ---------- Dữ liệu qua SQL (bỏ qua upload) ----------

    long newArtist() {
        return jdbc.queryForObject(
                "INSERT INTO artists(name, popularity) VALUES (?, 0) RETURNING id",
                Long.class, unique("artist-"));
    }

    long newSong(long artistId, String title) {
        return jdbc.queryForObject("""
                INSERT INTO songs(title, artist_id, duration_ms, audio_url, created_at)
                VALUES (?, ?, 1000, '/api/files/audio/test.mp3', now()) RETURNING id
                """, Long.class, title, artistId);
    }

    @BeforeEach
    void ready() {
        // chỗ dành cho khởi tạo chung nếu sau này cần
    }

    record Part(String name, String filename, String contentType, byte[] data) {
        static Part json(String name, String json) {
            return new Part(name, null, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }

        static Part file(String name, String filename, String contentType, byte[] data) {
            return new Part(name, filename, contentType, data);
        }
    }

    Resp multipart(String method, String path, String token, Part... parts) {
        String boundary = "----TestBoundary" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Part p : parts) {
            String disposition = "Content-Disposition: form-data; name=\"" + p.name() + "\""
                    + (p.filename() != null ? "; filename=\"" + p.filename() + "\"" : "");
            out.writeBytes(("--" + boundary + "\r\n" + disposition + "\r\nContent-Type: "
                    + p.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.writeBytes(p.data());
            out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(r.statusCode(), r.body());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // GET file thô (byte[]) để kiểm tra nội dung và header; headers là các cặp tên,
    // giá trị
    HttpResponse<byte[]> raw(String path, String... headers) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
            for (int i = 0; i + 1 < headers.length; i += 2) {
                b.header(headers[i], headers[i + 1]);
            }
            return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    long filesIn(String folder) {
        try (var s = Files.list(Path.of(uploadDir).toAbsolutePath().resolve(folder))) {
            return s.count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}