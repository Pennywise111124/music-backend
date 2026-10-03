package com.example.musicbackend;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UploadTest extends IntegrationTestBase {

    static final byte[] MP3 = "fake-mp3-bytes".getBytes();
    static final byte[] MP3_2 = "another-fake-mp3".getBytes();
    static final byte[] PNG = "fake-png-bytes".getBytes();

    private Part audio() {
        return Part.file("audio", "a.mp3", "audio/mpeg", MP3);
    }

    private Part cover() {
        return Part.file("cover", "c.png", "image/png", PNG);
    }

    private Part image() {
        return Part.file("image", "p.png", "image/png", PNG);
    }

    private String meta(long artistId) {
        return "{\"title\":\"T\",\"artistId\":%d,\"durationMs\":1000}".formatted(artistId);
    }

    private Resp createSong(TestUser admin, String metaJson, Part... files) {
        List<Part> parts = new ArrayList<>();
        parts.add(Part.json("metadata", metaJson));
        parts.addAll(List.of(files));
        return multipart("POST", "/api/admin/songs", admin == null ? null : admin.access(),
                parts.toArray(new Part[0]));
    }

    private boolean exists(String url) {
        return raw(url).statusCode() == 200;
    }

    private long newAlbum(long artistId) {
        return jdbc.queryForObject("INSERT INTO albums(title, artist_id) VALUES (?, ?) RETURNING id",
                Long.class, unique("album-"), artistId);
    }

    // ---------- Tạo song và phục vụ file ----------

    @Test
    void createSong_filesServedPublicly_withSafeHeadersAndRange() {
        TestUser admin = newAdmin();
        Resp r = createSong(admin, meta(newArtist()), audio(), cover());

        assertEquals(201, r.status());
        String a = r.str("$.audioUrl");
        String c = r.str("$.imageUrl");
        assertTrue(a.matches("/api/files/audio/[0-9a-f-]{36}\\.mp3"), a);
        assertTrue(c.matches("/api/files/images/[0-9a-f-]{36}\\.png"), c);

        HttpResponse<byte[]> f = raw(a); // không token
        assertEquals(200, f.statusCode());
        assertArrayEquals(MP3, f.body());
        assertEquals("audio/mpeg", f.headers().firstValue("Content-Type").orElse(""));

        HttpResponse<byte[]> part = raw(a, "Range", "bytes=0-3");
        assertEquals(206, part.statusCode());
        assertEquals(4, part.body().length);

        HttpResponse<byte[]> img = raw(c);
        assertEquals("image/png", img.headers().firstValue("Content-Type").orElse(""));
        assertEquals("nosniff", img.headers().firstValue("X-Content-Type-Options").orElse(""));
    }

    @Test
    void createSong_ignoresClientFilename() {
        TestUser admin = newAdmin();
        long before = filesIn("audio");

        Resp r = createSong(admin, meta(newArtist()),
                Part.file("audio", "../../evil.MP3", "audio/mpeg", MP3));

        assertEquals(201, r.status());
        assertTrue(r.str("$.audioUrl").matches("/api/files/audio/[0-9a-f-]{36}\\.mp3"), r.body());
        assertEquals(before + 1, filesIn("audio"));
    }

    @Test
    void createSong_accessAndContentTypeRules() {
        TestUser admin = newAdmin();
        TestUser user = newUser();
        long artist = newArtist();

        assertEquals(401, createSong(null, meta(artist), audio()).status());
        assertEquals(403, createSong(user, meta(artist), audio()).status());
        // JSON kiểu cũ vào endpoint multipart
        assertEquals(415, post("/api/admin/songs", admin.access(), meta(artist)).status());
        // metadata không khai là application/json
        assertEquals(415, multipart("POST", "/api/admin/songs", admin.access(),
                new Part("metadata", null, "text/plain", meta(artist).getBytes()), audio()).status());
        // thiếu part
        assertEquals(400, createSong(admin, meta(artist)).status());
        assertEquals(400, multipart("POST", "/api/admin/songs", admin.access(), audio()).status());
    }

    @Test
    void createSong_invalidFiles_rejected_andNothingLeftOnDisk() {
        TestUser admin = newAdmin();
        long artist = newArtist();
        long before = filesIn("audio");

        assertEquals(400, createSong(admin, meta(artist),
                Part.file("audio", "notes.txt", "audio/mpeg", MP3)).status());
        assertEquals(400, createSong(admin, meta(artist),
                Part.file("audio", "evil.exe", "audio/mpeg", MP3)).status());
        assertEquals(400, createSong(admin, meta(artist),
                Part.file("audio", "a.mp3", "text/plain", MP3)).status());
        assertEquals(400, createSong(admin, meta(artist),
                Part.file("audio", "empty.mp3", "audio/mpeg", new byte[0])).status());
        // vượt 20MB. Nếu dòng này chập chờn (kết nối bị đóng sớm), có thể bỏ riêng ca
        // này.
        assertEquals(413, createSong(admin, meta(artist),
                Part.file("audio", "big.mp3", "audio/mpeg", new byte[21 * 1024 * 1024])).status());

        assertEquals(before, filesIn("audio"));
    }

    @Test
    void createSong_badCover_rollsBackAlreadySavedAudio() {
        TestUser admin = newAdmin();
        long audioBefore = filesIn("audio");
        long imagesBefore = filesIn("images");

        Resp r = createSong(admin, meta(newArtist()), audio(),
                Part.file("cover", "a.mp3", "audio/mpeg", MP3)); // cover sai loại

        assertEquals(400, r.status());
        assertEquals(audioBefore, filesIn("audio")); // audio vừa lưu phải được dọn
        assertEquals(imagesBefore, filesIn("images"));
    }

    @Test
    void createSong_invalidMetadata_doesNotTouchDisk() {
        TestUser admin = newAdmin();
        long artist = newArtist();
        long otherAlbum = newAlbum(newArtist());
        long before = filesIn("audio");

        assertEquals(404, createSong(admin, meta(999999999L), audio()).status());
        assertEquals(400, createSong(admin,
                "{\"title\":\"T\",\"artistId\":%d,\"albumId\":%d,\"durationMs\":1000}"
                        .formatted(artist, otherAlbum),
                audio()).status());
        assertEquals(400, createSong(admin,
                "{\"artistId\":%d,\"durationMs\":1000}".formatted(artist), audio()).status());
        assertEquals(400, createSong(admin,
                "{\"title\":\"T\",\"artistId\":%d,\"durationMs\":1000,\"releaseDate\":\"05/06/2024\"}"
                        .formatted(artist),
                audio()).status());
        assertEquals(400, createSong(admin, "khong phai json", audio()).status());

        assertEquals(before, filesIn("audio"));
    }

    // ---------- Đổi và xóa file của song ----------

    @Test
    void replaceAudio_oldFileRemovedAfterCommit_newServed() {
        TestUser admin = newAdmin();
        Resp created = createSong(admin, meta(newArtist()), audio());
        long id = created.num("$.id");
        String oldUrl = created.str("$.audioUrl");

        Resp r = multipart("PUT", "/api/admin/songs/" + id + "/audio", admin.access(),
                Part.file("audio", "b.mp3", "audio/mpeg", MP3_2));

        assertEquals(200, r.status());
        String newUrl = r.str("$.audioUrl");
        assertNotEquals(oldUrl, newUrl);
        assertTrue(exists(newUrl));
        assertFalse(exists(oldUrl));
        assertArrayEquals(MP3_2, raw(newUrl).body());
    }

    @Test
    void replaceAudio_invalidOrUnknown_keepsOldFile_andLeavesNoNewFile() {
        TestUser admin = newAdmin();
        TestUser user = newUser();
        Resp created = createSong(admin, meta(newArtist()), audio());
        long id = created.num("$.id");
        String oldUrl = created.str("$.audioUrl");
        long before = filesIn("audio");

        assertEquals(400, multipart("PUT", "/api/admin/songs/" + id + "/audio", admin.access(),
                Part.file("audio", "notes.txt", "audio/mpeg", MP3)).status());
        assertEquals(404, multipart("PUT", "/api/admin/songs/999999999/audio", admin.access(),
                audio()).status());
        assertEquals(403, multipart("PUT", "/api/admin/songs/" + id + "/audio", user.access(),
                audio()).status());

        assertEquals(before, filesIn("audio"));
        assertTrue(exists(oldUrl));
        assertEquals(oldUrl, get("/api/songs/" + id, admin.access()).str("$.audioUrl"));
    }

    @Test
    void replaceCover_addsThenReplaces_oldCoverRemoved() {
        TestUser admin = newAdmin();
        Resp created = createSong(admin, meta(newArtist()), audio()); // chưa có cover
        long id = created.num("$.id");

        Resp first = multipart("PUT", "/api/admin/songs/" + id + "/cover", admin.access(),
                Part.file("cover", "c.png", "image/png", PNG));
        assertEquals(200, first.status());
        String firstUrl = first.str("$.imageUrl");
        assertTrue(exists(firstUrl));

        Resp second = multipart("PUT", "/api/admin/songs/" + id + "/cover", admin.access(),
                Part.file("cover", "c2.png", "image/png", "png2".getBytes()));
        assertEquals(200, second.status());
        assertFalse(exists(firstUrl));
        assertTrue(exists(second.str("$.imageUrl")));
    }

    @Test
    void deleteSong_removesAudioAndCoverFiles() {
        TestUser admin = newAdmin();
        Resp created = createSong(admin, meta(newArtist()), audio(), cover());
        String a = created.str("$.audioUrl");
        String c = created.str("$.imageUrl");
        assertTrue(exists(a) && exists(c));

        assertEquals(204, delete("/api/admin/songs/" + created.num("$.id"), admin.access()).status());

        assertFalse(exists(a));
        assertFalse(exists(c));
    }

    // ---------- Ảnh artist, album, playlist ----------

    @Test
    void artistImage_lifecycle() {
        TestUser admin = newAdmin();
        TestUser user = newUser();
        long artist = newArtist();
        String url = "/api/admin/artists/" + artist + "/image";

        Resp up = multipart("PUT", url, admin.access(), image());
        assertEquals(200, up.status());
        String first = up.str("$.imageUrl");
        assertTrue(exists(first));

        // PUT JSON không còn chạm tới imageUrl
        Resp rename = put("/api/admin/artists/" + artist, admin.access(), "{\"name\":\"Renamed\"}");
        assertEquals(200, rename.status());
        assertEquals(first, get("/api/artists/" + artist, admin.access()).str("$.imageUrl"));

        // đổi ảnh: ảnh cũ bị xóa
        Resp again = multipart("PUT", url, admin.access(),
                Part.file("image", "p2.png", "image/png", "png2".getBytes()));
        String second = again.str("$.imageUrl");
        assertFalse(exists(first));
        assertTrue(exists(second));

        // sai loại, không tồn tại, không đủ quyền
        assertEquals(400, multipart("PUT", url, admin.access(),
                Part.file("image", "n.txt", "image/png", PNG)).status());
        assertEquals(second, get("/api/artists/" + artist, admin.access()).str("$.imageUrl"));
        assertEquals(404, multipart("PUT", "/api/admin/artists/999999999/image", admin.access(),
                image()).status());
        assertEquals(403, multipart("PUT", url, user.access(), image()).status());

        // xóa artist dọn luôn ảnh
        assertEquals(204, delete("/api/admin/artists/" + artist, admin.access()).status());
        assertFalse(exists(second));
    }

    @Test
    void albumImage_lifecycle() {
        TestUser admin = newAdmin();
        long album = newAlbum(newArtist());
        String url = "/api/admin/albums/" + album + "/image";

        Resp up = multipart("PUT", url, admin.access(), image());
        assertEquals(200, up.status());
        String imageUrl = up.str("$.imageUrl");
        assertTrue(exists(imageUrl));

        long artistId = up.num("$.artistId");
        Resp rename = put("/api/admin/albums/" + album, admin.access(),
                "{\"title\":\"Renamed\",\"artistId\":" + artistId + "}");
        assertEquals(200, rename.status());
        assertEquals(imageUrl, rename.str("$.imageUrl"));

        assertEquals(404, multipart("PUT", "/api/admin/albums/999999999/image", admin.access(),
                image()).status());

        assertEquals(204, delete("/api/admin/albums/" + album, admin.access()).status());
        assertFalse(exists(imageUrl));
    }

    @Test
    void playlistImage_ownerOnly_survivesRename_removedOnDelete() {
        TestUser owner = newUser();
        TestUser other = newUser();
        TestUser admin = newAdmin();
        long pub = post("/api/playlists", owner.access(), "{\"name\":\"Pub\",\"isPublic\":true}").num("$.id");
        long priv = post("/api/playlists", owner.access(), "{\"name\":\"Priv\"}").num("$.id");

        Resp up = multipart("PUT", "/api/playlists/" + pub + "/image", owner.access(), image());
        assertEquals(200, up.status());
        String imageUrl = up.str("$.imageUrl");
        assertTrue(exists(imageUrl));

        assertEquals(401, multipart("PUT", "/api/playlists/" + pub + "/image", null, image()).status());
        assertEquals(403, multipart("PUT", "/api/playlists/" + pub + "/image", other.access(),
                image()).status());
        assertEquals(403, multipart("PUT", "/api/playlists/" + pub + "/image", admin.access(),
                image()).status());
        assertEquals(404, multipart("PUT", "/api/playlists/" + priv + "/image", other.access(),
                image()).status());

        Resp rename = put("/api/playlists/" + pub, owner.access(), "{\"name\":\"Pub2\",\"isPublic\":true}");
        assertEquals(imageUrl, rename.str("$.imageUrl"));

        assertEquals(204, delete("/api/playlists/" + pub, owner.access()).status());
        assertFalse(exists(imageUrl));
    }

    @Test
    void deleteUser_removesTheirPlaylistImages() {
        TestUser admin = newAdmin();
        TestUser victim = newUser();
        long playlist = post("/api/playlists", victim.access(), "{\"name\":\"Mine\"}").num("$.id");
        String imageUrl = multipart("PUT", "/api/playlists/" + playlist + "/image", victim.access(),
                image()).str("$.imageUrl");
        assertTrue(exists(imageUrl));

        assertEquals(204, delete("/api/admin/users/" + victim.id(), admin.access()).status());

        assertFalse(exists(imageUrl));
    }

    // ---------- Phục vụ file: tên lạ ----------

    @Test
    void fileServing_rejectsUnexpectedNames() {
        String zero = "00000000-0000-0000-0000-000000000000";
        assertEquals(404, raw("/api/files/audio/" + zero + ".mp3").statusCode());
        assertEquals(404, raw("/api/files/audio/hello.mp3").statusCode());
        assertEquals(404, raw("/api/files/video/" + zero + ".mp4").statusCode());
        assertEquals(404, raw("/api/files/images/" + zero + ".mp3").statusCode());
        assertNotEquals(200, raw("/api/files/audio/..%2F..%2Fapplication.yml").statusCode());
    }
}