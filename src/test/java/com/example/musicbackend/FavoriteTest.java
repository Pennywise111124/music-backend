package com.example.musicbackend;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class FavoriteTest extends IntegrationTestBase {

    private void fav(TestUser u, long song) {
        assertEquals(204, post("/api/favorites/" + song, u.access(), null).status());
    }

    private List<String> titles(TestUser u) {
        Resp r = get("/api/favorites?size=50", u.access());
        assertEquals(200, r.status());
        return r.list("$.content[*].title");
    }

    private long rows(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM favorite_songs WHERE user_id = ?",
                Long.class, userId);
    }

    @Test
    void unauthenticated_unauthorized() {
        assertEquals(401, get("/api/favorites", null).status());
        assertEquals(401, post("/api/favorites/1", null, null).status());
        assertEquals(401, delete("/api/favorites/1", null).status());
    }

    @Test
    void add_isIdempotent_newestFirst_removeAndReAdd() {
        TestUser u = newUser();
        long artist = newArtist();
        long s1 = newSong(artist, "Alpha");
        long s2 = newSong(artist, "Beta");

        fav(u, s1);
        fav(u, s2);
        fav(u, s1); // thêm lại: không tạo dòng mới
        assertEquals(List.of("Beta", "Alpha"), titles(u));
        assertEquals(2, rows(u.id()));

        assertEquals(204, delete("/api/favorites/" + s1, u.access()).status());
        assertEquals(204, delete("/api/favorites/" + s1, u.access()).status()); // lặp lại vẫn OK
        assertEquals(List.of("Beta"), titles(u));

        fav(u, s1); // thêm lại sau khi bỏ: lên đầu
        assertEquals(List.of("Alpha", "Beta"), titles(u));
    }

    @Test
    void invalidInput() {
        TestUser u = newUser();
        assertEquals(404, post("/api/favorites/999999999", u.access(), null).status());
        assertEquals(204, delete("/api/favorites/999999999", u.access()).status());
        assertEquals(400, post("/api/favorites/abc", u.access(), null).status());
        assertEquals(400, delete("/api/favorites/abc", u.access()).status());
        assertEquals(400, get("/api/favorites?size=abc", u.access()).status());
    }

    @Test
    void favorites_isolatedPerUser_andRemoveOnlyAffectsCaller() {
        TestUser a = newUser();
        TestUser b = newUser();
        long song = newSong(newArtist(), "Shared");
        fav(a, song);
        fav(b, song);

        assertEquals(204, delete("/api/favorites/" + song, a.access()).status());
        assertEquals(0, titles(a).size());
        assertEquals(List.of("Shared"), titles(b));

        // bỏ bài mình chưa từng thích không đụng tới người khác
        assertEquals(204, delete("/api/favorites/" + song, a.access()).status());
        assertEquals(List.of("Shared"), titles(b));
    }

    @Test
    void paging_clampsParameters() {
        TestUser u = newUser();
        long artist = newArtist();
        for (int i = 0; i < 3; i++) {
            fav(u, newSong(artist, "P-" + i));
        }

        Resp page = get("/api/favorites?page=1&size=2", u.access());
        assertEquals(1, page.list("$.content").size());
        assertEquals(3, page.num("$.totalElements"));
        assertEquals(2, page.num("$.totalPages"));

        assertEquals(1, get("/api/favorites?size=0", u.access()).num("$.size"));
        assertEquals(50, get("/api/favorites?size=1000", u.access()).num("$.size"));
        assertEquals(0, get("/api/favorites?page=-1", u.access()).num("$.page"));
    }

    @Test
    void deleteSong_removesFavorites() {
        TestUser admin = newAdmin();
        TestUser u = newUser();
        long artist = newArtist();
        long keep = newSong(artist, "Keep");
        long gone = newSong(artist, "Gone");
        fav(u, keep);
        fav(u, gone);

        assertEquals(204, delete("/api/admin/songs/" + gone, admin.access()).status());
        assertEquals(List.of("Keep"), titles(u));
    }

    @Test
    void concurrentAdd_sameUserSameSong_allSucceed_singleRow() throws Exception {
        TestUser u = newUser();
        long song = newSong(newArtist(), "Race");
        int n = 6;

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return post("/api/favorites/" + song, u.access(), null).status();
            }));
        }
        start.countDown();
        for (Future<Integer> f : futures) {
            assertEquals(204, f.get()); // trước khi sửa: có thể ra 409
        }
        pool.shutdown();

        assertEquals(1, rows(u.id()));
    }
}