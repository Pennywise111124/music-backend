package com.example.musicbackend;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class FollowTest extends IntegrationTestBase {

    private long popularity(long artistId) {
        return jdbc.queryForObject("SELECT popularity FROM artists WHERE id = ?", Long.class, artistId);
    }

    private long count(String table, String column, long value) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, value);
    }

    private String artistName(long id) {
        return jdbc.queryForObject("SELECT name FROM artists WHERE id = ?", String.class, id);
    }

    private void follow(TestUser u, long artist) {
        assertEquals(204, post("/api/artists/" + artist + "/follow", u.access(), null).status());
    }

    @Test
    void unauthenticated_unauthorized() {
        long artist = newArtist();
        assertEquals(401, post("/api/artists/" + artist + "/follow", null, null).status());
        assertEquals(401, delete("/api/artists/" + artist + "/follow", null).status());
        assertEquals(401, get("/api/artists/followed", null).status());
    }

    @Test
    void follow_isIdempotent_popularityCountedOnce_followedFlagPerUser() {
        TestUser a = newUser();
        TestUser b = newUser();
        long artist = newArtist();

        follow(a, artist);
        follow(a, artist); // lặp lại: không tăng thêm

        assertEquals(1, popularity(artist));
        assertEquals(1, count("followed_artists", "artist_id", artist));

        Resp ra = get("/api/artists/" + artist, a.access());
        assertEquals(200, ra.status());
        assertEquals(true, ra.<Boolean>read("$.followed"));
        assertEquals(1, ra.num("$.popularity"));

        // người khác không thấy followed
        assertEquals(false, get("/api/artists/" + artist, b.access()).<Boolean>read("$.followed"));

        // danh sách tìm kiếm cũng tính followed theo từng user
        String q = "/api/artists?query=" + artistName(artist);
        assertEquals(true, get(q, a.access()).<Boolean>read("$[0].followed"));
        assertEquals(false, get(q, b.access()).<Boolean>read("$[0].followed"));

        follow(b, artist);
        assertEquals(2, popularity(artist));
    }

    @Test
    void unfollow_isIdempotent_neverNegative_onlyAffectsOwnFollow() {
        TestUser a = newUser();
        TestUser b = newUser();
        long artist = newArtist();
        follow(a, artist);
        follow(b, artist);

        assertEquals(204, delete("/api/artists/" + artist + "/follow", a.access()).status());
        assertEquals(204, delete("/api/artists/" + artist + "/follow", a.access()).status()); // lặp lại
        assertEquals(1, popularity(artist));
        assertEquals(true, get("/api/artists/" + artist, b.access()).<Boolean>read("$.followed"));

        // chưa từng follow artist khác: không giảm
        long other = newArtist();
        assertEquals(204, delete("/api/artists/" + other + "/follow", a.access()).status());
        assertEquals(0, popularity(other));
    }

    @Test
    void unknownArtist_followIs404_unfollowIs204() {
        TestUser u = newUser();
        assertEquals(404, post("/api/artists/999999999/follow", u.access(), null).status());
        assertEquals(204, delete("/api/artists/999999999/follow", u.access()).status());
        assertEquals(400, post("/api/artists/abc/follow", u.access(), null).status());
    }

    @Test
    void followedList_newestFirst_paged_andIsolatedPerUser() {
        TestUser a = newUser();
        TestUser b = newUser();
        long first = newArtist();
        long second = newArtist();
        follow(a, first);
        follow(a, second);

        Resp r = get("/api/artists/followed", a.access());
        assertEquals(200, r.status());
        assertEquals(2, r.num("$.totalElements"));
        assertEquals(second, r.num("$.content[0].id"));
        assertEquals(first, r.num("$.content[1].id"));
        assertEquals(true, r.<Boolean>read("$.content[0].followed"));

        Resp page = get("/api/artists/followed?page=1&size=1", a.access());
        assertEquals(first, page.num("$.content[0].id"));
        assertEquals(2, page.num("$.totalPages"));

        assertEquals(0, get("/api/artists/followed", b.access()).num("$.totalElements"));
    }

    @Test
    void deleteArtist_withFollowers_ok_andCleansFollows() {
        TestUser admin = newAdmin();
        TestUser u = newUser();
        long artist = newArtist();
        follow(u, artist);
        follow(admin, artist);

        assertEquals(204, delete("/api/admin/artists/" + artist, admin.access()).status());
        assertEquals(0, count("followed_artists", "artist_id", artist));
        assertEquals(404, get("/api/artists/" + artist, u.access()).status());
        assertEquals(0, get("/api/artists/followed", u.access()).num("$.totalElements"));
    }

    @Test
    void concurrentFollow_sameUser_countsOnce() throws Exception {
        TestUser u = newUser();
        long artist = newArtist();
        int n = 6;

        List<Integer> statuses = runConcurrently(n,
                i -> post("/api/artists/" + artist + "/follow", u.access(), null).status());

        statuses.forEach(s -> assertEquals(204, s));
        // ON CONFLICT DO NOTHING + chỉ tăng khi chèn được
        assertEquals(1, popularity(artist));
        assertEquals(1, count("followed_artists", "artist_id", artist));
    }

    @Test
    void concurrentFollow_differentUsers_noLostUpdates() throws Exception {
        long artist = newArtist();
        int n = 6;
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            users.add(newUser());
        }

        List<Integer> statuses = runConcurrently(n,
                i -> post("/api/artists/" + artist + "/follow", users.get(i).access(), null).status());

        statuses.forEach(s -> assertEquals(204, s));
        // nếu đọc rồi ghi lại thay vì UPDATE atomic, số này sẽ nhỏ hơn n
        assertEquals(n, popularity(artist));
    }

    @Test
    void unfollow_neverMakesPopularityNegative_evenIfDataIsInconsistent() {
        TestUser u = newUser();
        long artist = newArtist();
        follow(u, artist);
        jdbc.update("UPDATE artists SET popularity = 0 WHERE id = ?", artist); // cố tình làm lệch dữ liệu

        assertEquals(204, delete("/api/artists/" + artist + "/follow", u.access()).status());
        assertEquals(0, popularity(artist)); // thiếu "popularity > 0" thì ra -1
    }

    private interface Job {
        int run(int index);
    }

    private List<Integer> runConcurrently(int n, Job job) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                return job.run(idx);
            }));
        }
        start.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }
}