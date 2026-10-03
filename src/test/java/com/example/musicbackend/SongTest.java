package com.example.musicbackend;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SongTest extends IntegrationTestBase {

    private long newAlbum(long artistId) {
        return jdbc.queryForObject("INSERT INTO albums(title, artist_id) VALUES (?, ?) RETURNING id",
                Long.class, unique("album-"), artistId);
    }

    private long newGenre() {
        return jdbc.queryForObject("INSERT INTO genres(name) VALUES (?) RETURNING id",
                Long.class, unique("genre-"));
    }

    private long songWith(long artist, Long album, Long genre, String title) {
        return jdbc.queryForObject("""
                INSERT INTO songs(title, artist_id, album_id, genre_id, duration_ms, audio_url, image_url, created_at)
                VALUES (?, ?, ?, ?, 1000, '/api/files/audio/orig.mp3', '/api/files/images/orig.png', now())
                RETURNING id
                """, Long.class, title, artist, album, genre);
    }

    private String meta(String title, long artistId, Long albumId, Long genreId) {
        StringBuilder sb = new StringBuilder("{\"title\":\"" + title + "\",\"artistId\":" + artistId
                + ",\"durationMs\":2000");
        if (albumId != null) {
            sb.append(",\"albumId\":").append(albumId);
        }
        if (genreId != null) {
            sb.append(",\"genreId\":").append(genreId);
        }
        return sb.append("}").toString();
    }

    // ---------- Đọc ----------

    @Test
    void authentication_andAdminOnlyWrites() {
        TestUser user = newUser();
        assertEquals(401, get("/api/songs", null).status());
        assertEquals(403, put("/api/admin/songs/1", user.access(), "{}").status());
        assertEquals(403, delete("/api/admin/songs/1", user.access()).status());
        assertEquals(401, delete("/api/admin/songs/1", null).status());
    }

    @Test
    void getById_ok_notFound_badId() {
        TestUser u = newUser();
        long artist = newArtist();
        long album = newAlbum(artist);
        long genre = newGenre();
        long song = songWith(artist, album, genre, "Full");

        Resp r = get("/api/songs/" + song, u.access());
        assertEquals(200, r.status());
        assertEquals("Full", r.str("$.title"));
        assertEquals(artist, r.num("$.artistId"));
        assertEquals(album, r.num("$.albumId"));
        assertEquals(genre, r.num("$.genreId"));

        assertEquals(404, get("/api/songs/999999999", u.access()).status());
        assertEquals(400, get("/api/songs/abc", u.access()).status());
    }

    @Test
    void search_caseInsensitive_sortedByTitle_paged() {
        TestUser u = newUser();
        long artist = newArtist();
        String p = unique("sx").toLowerCase();
        songWith(artist, null, null, p + "-c");
        songWith(artist, null, null, p + "-a");
        songWith(artist, null, null, p + "-b");

        Resp all = get("/api/songs?query=" + p.toUpperCase(), u.access());
        assertEquals(200, all.status());
        assertEquals(List.of(p + "-a", p + "-b", p + "-c"), all.list("$.content[*].title"));

        Resp page2 = get("/api/songs?query=" + p + "&page=1&size=2", u.access());
        assertEquals(List.of(p + "-c"), page2.list("$.content[*].title"));
        assertEquals(3, page2.num("$.totalElements"));
        assertEquals(2, page2.num("$.totalPages"));

        assertEquals(0, get("/api/songs?query=" + p + "zzzz", u.access()).num("$.totalElements"));
    }

    @Test
    void paging_clampsParameters() {
        TestUser u = newUser();
        assertEquals(1, get("/api/songs?size=0", u.access()).num("$.size"));
        assertEquals(50, get("/api/songs?size=1000", u.access()).num("$.size"));
        assertEquals(0, get("/api/songs?page=-1", u.access()).num("$.page"));
        assertEquals(400, get("/api/songs?size=abc", u.access()).status());
    }

    @Test
    void byArtist_byAlbum_andUnknownIs404() {
        TestUser u = newUser();
        long artist = newArtist();
        long emptyArtist = newArtist();
        long album = newAlbum(artist);
        songWith(artist, album, null, "InAlbum");
        songWith(artist, null, null, "Single");

        assertEquals(List.of("InAlbum", "Single"),
                get("/api/songs/by-artist/" + artist, u.access()).list("$[*].title"));
        assertEquals(0, get("/api/songs/by-artist/" + emptyArtist, u.access()).list("$").size());
        assertEquals(List.of("InAlbum"),
                get("/api/songs/by-album/" + album, u.access()).list("$[*].title"));

        assertEquals(404, get("/api/songs/by-artist/999999999", u.access()).status());
        assertEquals(404, get("/api/songs/by-album/999999999", u.access()).status());
        assertEquals(400, get("/api/songs/by-artist/abc", u.access()).status());
    }

    // ---------- Sửa metadata ----------

    @Test
    void update_validatesRelations_andReplacesWholeMetadata() {
        TestUser admin = newAdmin();
        long artistA = newArtist();
        long artistB = newArtist();
        long albumA = newAlbum(artistA);
        long albumB = newAlbum(artistB);
        long genre = newGenre();
        long song = songWith(artistA, albumA, genre, "Before");
        String url = "/api/admin/songs/" + song;

        // album của artist khác -> 400
        assertEquals(400, put(url, admin.access(), meta("X", artistA, albumB, null)).status());
        // không tồn tại -> 404
        assertEquals(404, put(url, admin.access(), meta("X", 999999999L, null, null)).status());
        assertEquals(404, put(url, admin.access(), meta("X", artistA, 999999999L, null)).status());
        assertEquals(404, put(url, admin.access(), meta("X", artistA, null, 999999999L)).status());
        assertEquals(404, put("/api/admin/songs/999999999", admin.access(),
                meta("X", artistA, null, null)).status());
        // dữ liệu không hợp lệ -> 400
        assertEquals(400, put(url, admin.access(), meta("", artistA, null, null)).status());
        assertEquals(400, put(url, admin.access(),
                "{\"title\":\"X\",\"artistId\":%d,\"durationMs\":0}".formatted(artistA)).status());

        // các lần sai không làm đổi dữ liệu
        assertEquals("Before", get("/api/songs/" + song, admin.access()).str("$.title"));

        // PUT thiếu albumId, genreId: về null (thay thế toàn bộ); trim title
        Resp ok = put(url, admin.access(), meta("  After  ", artistA, null, null));
        assertEquals(200, ok.status());
        assertEquals("After", ok.str("$.title"));
        assertNull(ok.<Object>read("$.albumId"));
        assertNull(ok.<Object>read("$.genreId"));
    }

    @Test
    void update_neverTouchesFileUrls() {
        TestUser admin = newAdmin();
        long artist = newArtist();
        long song = songWith(artist, null, null, "Files");

        Resp r = put("/api/admin/songs/" + song, admin.access(),
                "{\"title\":\"Files2\",\"artistId\":%d,\"durationMs\":3000,\"audioUrl\":\"http://hack/x.mp3\"}"
                        .formatted(artist));

        assertEquals(200, r.status());
        assertEquals("/api/files/audio/orig.mp3", r.str("$.audioUrl"));
        assertEquals("/api/files/images/orig.png", r.str("$.imageUrl"));
    }

    // ---------- Ràng buộc "đang được dùng" ----------

    @Test
    void genreAlbumArtistInUse_cannotBeDeleted_untilSongIsRemoved() {
        TestUser admin = newAdmin();
        long artist = newArtist();
        long album = newAlbum(artist);
        long genre = newGenre();
        long song = songWith(artist, album, genre, "Used");

        Resp g = delete("/api/admin/genres/" + genre, admin.access());
        assertEquals(409, g.status());
        assertTrue(g.str("$.message").contains("Cannot delete genre"), g.body());

        Resp al = delete("/api/admin/albums/" + album, admin.access());
        assertEquals(409, al.status());
        assertTrue(al.str("$.message").contains("Cannot delete album"), al.body());

        Resp ar = delete("/api/admin/artists/" + artist, admin.access());
        assertEquals(409, ar.status());
        assertTrue(ar.str("$.message").contains("Cannot delete artist"), ar.body());

        assertEquals(204, delete("/api/admin/songs/" + song, admin.access()).status());
        assertEquals(404, get("/api/songs/" + song, admin.access()).status());

        assertEquals(204, delete("/api/admin/genres/" + genre, admin.access()).status());
        assertEquals(204, delete("/api/admin/albums/" + album, admin.access()).status());
        assertEquals(204, delete("/api/admin/artists/" + artist, admin.access()).status());
    }

    @Test
    void albumWithSongs_cannotChangeArtist_butCanBeRenamed() {
        TestUser admin = newAdmin();
        long artistA = newArtist();
        long artistB = newArtist();
        long album = newAlbum(artistA);
        songWith(artistA, album, null, "Inside");

        assertEquals(409, put("/api/admin/albums/" + album, admin.access(),
                "{\"title\":\"Moved\",\"artistId\":" + artistB + "}").status());
        assertEquals(200, put("/api/admin/albums/" + album, admin.access(),
                "{\"title\":\"Renamed\",\"artistId\":" + artistA + "}").status());
    }
}