package com.example.musicbackend;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistTest extends IntegrationTestBase {

    private long createPlaylist(TestUser owner, String name, boolean isPublic) {
        Resp r = post("/api/playlists", owner.access(),
                "{\"name\":\"%s\",\"isPublic\":%s}".formatted(name, isPublic));
        assertEquals(201, r.status());
        return r.num("$.id");
    }

    private void add(TestUser u, long playlist, long song) {
        assertEquals(204, post("/api/playlists/" + playlist + "/songs/" + song, u.access(), null).status());
    }

    private List<String> titles(TestUser u, long playlist) {
        Resp r = get("/api/playlists/" + playlist, u.access());
        assertEquals(200, r.status());
        return r.list("$.songs[*].title");
    }

    // ---------- Tạo, quyền xem ----------

    @Test
    void create_trimsName_defaultsPrivate_ownerFromToken() {
        TestUser u = newUser();
        Resp r = post("/api/playlists", u.access(), "{\"name\":\"  My List  \"}");

        assertEquals(201, r.status());
        assertEquals("My List", r.str("$.name"));
        assertEquals(false, r.<Boolean>read("$.isPublic"));
        assertEquals(u.username(), r.str("$.ownerName"));
        assertEquals(0, r.num("$.songCount"));
    }

    @Test
    void create_invalid() {
        TestUser u = newUser();
        assertEquals(400, post("/api/playlists", u.access(), "{\"name\":\"   \"}").status());
        assertEquals(400, post("/api/playlists", u.access(), "{}").status());
        assertEquals(401, post("/api/playlists", null, "{\"name\":\"x\"}").status());
    }

    @Test
    void privatePlaylist_hiddenFromEveryoneElse_includingAdmin() {
        TestUser owner = newUser();
        TestUser other = newUser();
        TestUser admin = newAdmin();
        long id = createPlaylist(owner, "Secret", false);

        assertEquals(200, get("/api/playlists/" + id, owner.access()).status());
        assertEquals(404, get("/api/playlists/" + id, other.access()).status());
        assertEquals(404, get("/api/playlists/" + id, admin.access()).status());
    }

    @Test
    void publicPlaylist_visibleToOthers_butOnlyOwnerCanModify() {
        TestUser owner = newUser();
        TestUser other = newUser();
        long id = createPlaylist(owner, "Open", true);
        long song = newSong(newArtist(), unique("s-"));

        assertEquals(200, get("/api/playlists/" + id, other.access()).status());
        assertEquals(403, post("/api/playlists/" + id + "/songs/" + song, other.access(), null).status());
        assertEquals(403, delete("/api/playlists/" + id + "/songs/" + song, other.access()).status());
        assertEquals(403, put("/api/playlists/" + id, other.access(), "{\"name\":\"Hack\"}").status());
        assertEquals(403, put("/api/playlists/" + id + "/reorder", other.access(),
                "{\"songIds\":[]}").status());
        assertEquals(403, delete("/api/playlists/" + id, other.access()).status());
    }

    @Test
    void privatePlaylist_modifyByOthers_looksLikeNotFound() {
        TestUser owner = newUser();
        TestUser other = newUser();
        long id = createPlaylist(owner, "Hidden", false);
        long song = newSong(newArtist(), unique("s-"));

        assertEquals(404, post("/api/playlists/" + id + "/songs/" + song, other.access(), null).status());
        assertEquals(404, put("/api/playlists/" + id, other.access(), "{\"name\":\"Hack\"}").status());
        assertEquals(404, delete("/api/playlists/" + id, other.access()).status());
        assertEquals(404, delete("/api/playlists/999999999", owner.access()).status());
    }

    @Test
    void listings_mineAndPublic() {
        TestUser a = newUser();
        TestUser b = newUser();
        String pubName = unique("Pub-");
        createPlaylist(a, pubName, true);
        createPlaylist(a, unique("Priv-"), false);
        createPlaylist(b, unique("Other-"), false);

        Resp mine = get("/api/playlists/me", a.access());
        assertEquals(200, mine.status());
        assertEquals(2, mine.num("$.totalElements"));

        Resp pub = get("/api/playlists/public?query=" + pubName.toUpperCase(), b.access());
        assertEquals(200, pub.status());
        assertEquals(1, pub.num("$.totalElements"));
        assertEquals(pubName, pub.str("$.content[0].name"));
    }

    // ---------- Bài hát và thứ tự ----------

    @Test
    void addSongs_keepsOrder_idempotent_unknownSong404() {
        TestUser u = newUser();
        long artist = newArtist();
        long s1 = newSong(artist, "One");
        long s2 = newSong(artist, "Two");
        long s3 = newSong(artist, "Three");
        long id = createPlaylist(u, "Order", false);

        add(u, id, s1);
        add(u, id, s2);
        add(u, id, s3);
        add(u, id, s1); // thêm lại: không tạo dòng mới, không đổi thứ tự

        assertEquals(List.of("One", "Two", "Three"), titles(u, id));
        assertEquals(3, get("/api/playlists/" + id, u.access()).num("$.songCount"));
        assertEquals(404, post("/api/playlists/" + id + "/songs/999999999", u.access(), null).status());
    }

    @Test
    void reorder_valid_andInvalidRequestsDoNotChangeAnything() {
        TestUser u = newUser();
        long artist = newArtist();
        long s1 = newSong(artist, "One");
        long s2 = newSong(artist, "Two");
        long s3 = newSong(artist, "Three");
        long id = createPlaylist(u, "Reorder", false);
        add(u, id, s1);
        add(u, id, s2);
        add(u, id, s3);

        assertEquals(204, put("/api/playlists/" + id + "/reorder", u.access(),
                "{\"songIds\":[%d,%d,%d]}".formatted(s3, s1, s2)).status());
        assertEquals(List.of("Three", "One", "Two"), titles(u, id));

        // thiếu, id lạ, trùng, thiếu field: đều 400 và thứ tự giữ nguyên
        assertEquals(400, put("/api/playlists/" + id + "/reorder", u.access(),
                "{\"songIds\":[%d,%d]}".formatted(s1, s2)).status());
        assertEquals(400, put("/api/playlists/" + id + "/reorder", u.access(),
                "{\"songIds\":[%d,%d,999999999]}".formatted(s1, s2)).status());
        assertEquals(400, put("/api/playlists/" + id + "/reorder", u.access(),
                "{\"songIds\":[%d,%d,%d]}".formatted(s3, s3, s1)).status());
        assertEquals(400, put("/api/playlists/" + id + "/reorder", u.access(), "{}").status());

        assertEquals(List.of("Three", "One", "Two"), titles(u, id));
    }

    @Test
    void removeSong_compactsPositions_idempotent_reAddGoesLast() {
        TestUser u = newUser();
        long artist = newArtist();
        long s1 = newSong(artist, "One");
        long s2 = newSong(artist, "Two");
        long s3 = newSong(artist, "Three");
        long id = createPlaylist(u, "Remove", false);
        add(u, id, s1);
        add(u, id, s2);
        add(u, id, s3);

        assertEquals(204, delete("/api/playlists/" + id + "/songs/" + s2, u.access()).status());
        assertEquals(204, delete("/api/playlists/" + id + "/songs/" + s2, u.access()).status());
        assertEquals(204, delete("/api/playlists/" + id + "/songs/999999999", u.access()).status());
        assertEquals(List.of("One", "Three"), titles(u, id));
        assertEquals(List.of(0, 1), positions(id));

        add(u, id, s2);
        assertEquals(List.of("One", "Three", "Two"), titles(u, id));
        assertEquals(List.of(0, 1, 2), positions(id));
    }

    // ---------- Chống race khi thêm bài đồng thời ----------

    @Test
    void concurrentAddSong_assignsUniqueContiguousPositions() throws Exception {
        TestUser u = newUser();
        long artist = newArtist();
        long id = createPlaylist(u, "Race", false);
        int n = 6;

        List<Long> songs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            songs.add(newSong(artist, "Race-" + i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (long song : songs) {
            results.add(pool.submit(() -> {
                start.await();
                return post("/api/playlists/" + id + "/songs/" + song, u.access(), null).status();
            }));
        }
        start.countDown();
        for (Future<Integer> f : results) {
            assertEquals(204, f.get());
        }
        pool.shutdown();

        // nếu thiếu khóa dòng, hai request cùng đọc max và gán trùng position
        assertEquals(IntStream.range(0, n).boxed().toList(), positions(id));
    }

    // ---------- Sửa, xóa ----------

    @Test
    void update_isFullReplace_andCanTogglePublic() {
        TestUser owner = newUser();
        TestUser other = newUser();
        long id = createPlaylist(owner, "Before", false);

        Resp r = put("/api/playlists/" + id, owner.access(),
                "{\"name\":\"After\",\"description\":\"d\",\"isPublic\":true}");
        assertEquals(200, r.status());
        assertEquals("After", r.str("$.name"));
        assertEquals(200, get("/api/playlists/" + id, other.access()).status());

        // PUT thiếu isPublic và description: về false và null
        Resp r2 = put("/api/playlists/" + id, owner.access(), "{\"name\":\"After\"}");
        assertEquals(200, r2.status());
        assertEquals(false, r2.<Boolean>read("$.isPublic"));
        assertEquals(404, get("/api/playlists/" + id, other.access()).status());

        assertEquals(400, put("/api/playlists/" + id, owner.access(), "{\"name\":\"\"}").status());
    }

    @Test
    void deletePlaylist_withSongs_ok_thenGone() {
        TestUser u = newUser();
        long song = newSong(newArtist(), unique("s-"));
        long id = createPlaylist(u, "Bye", false);
        add(u, id, song);

        assertEquals(204, delete("/api/playlists/" + id, u.access()).status());
        assertEquals(404, delete("/api/playlists/" + id, u.access()).status());
        assertEquals(404, get("/api/playlists/" + id, u.access()).status());
        assertEquals(200, get("/api/songs/" + song, u.access()).status()); // bài hát còn
    }

    @Test
    void deleteSong_removesItFromPlaylistsFavoritesAndHistory() {
        TestUser admin = newAdmin();
        TestUser u = newUser();
        long artist = newArtist();
        long keep = newSong(artist, "Keep");
        long gone = newSong(artist, "Gone");
        long id = createPlaylist(u, "WithSongs", false);
        add(u, id, keep);
        add(u, id, gone);
        assertEquals(204, post("/api/favorites/" + gone, u.access(), null).status());
        assertEquals(204, post("/api/recently-played/" + gone, u.access(), null).status());

        assertEquals(204, delete("/api/admin/songs/" + gone, admin.access()).status());

        assertEquals(List.of("Keep"), titles(u, id));
        assertEquals(1, get("/api/playlists/" + id, u.access()).num("$.songCount"));
        assertEquals(0, get("/api/favorites", u.access()).num("$.totalElements"));
        assertEquals(0, get("/api/recently-played", u.access()).list("$").size());
    }

    private List<Integer> positions(long playlistId) {
        return jdbc.queryForList(
                "SELECT \"position\" FROM playlist_songs WHERE playlist_id = ? ORDER BY \"position\"",
                Integer.class, playlistId);
    }
}