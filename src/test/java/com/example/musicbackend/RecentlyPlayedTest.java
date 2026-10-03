package com.example.musicbackend;

import com.example.musicbackend.service.RecentlyPlayedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecentlyPlayedTest extends IntegrationTestBase {

    @Autowired
    RecentlyPlayedService recentlyPlayedService;

    private void play(TestUser u, long song) {
        assertEquals(204, post("/api/recently-played/" + song, u.access(), null).status());
    }

    private List<String> history(TestUser u) {
        Resp r = get("/api/recently-played", u.access());
        assertEquals(200, r.status());
        return r.list("$[*].title");
    }

    private long rows(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM recently_played WHERE user_id = ?",
                Long.class, userId);
    }

    @Test
    void unauthenticated_unauthorized() {
        assertEquals(401, get("/api/recently-played", null).status());
        assertEquals(401, post("/api/recently-played/1", null, null).status());
        assertEquals(401, delete("/api/recently-played", null).status());
    }

    @Test
    void record_newestFirst_replayMovesToTopWithoutDuplicates() {
        TestUser u = newUser();
        long artist = newArtist();
        long s1 = newSong(artist, "One");
        long s2 = newSong(artist, "Two");
        long s3 = newSong(artist, "Three");

        play(u, s1);
        play(u, s2);
        play(u, s3);
        assertEquals(List.of("Three", "Two", "One"), history(u));

        play(u, s1); // nghe lại: lên đầu, không tạo dòng mới
        assertEquals(List.of("One", "Three", "Two"), history(u));
        assertEquals(3, rows(u.id()));
    }

    @Test
    void record_unknownSong404_badId400() {
        TestUser u = newUser();
        assertEquals(404, post("/api/recently-played/999999999", u.access(), null).status());
        assertEquals(400, post("/api/recently-played/abc", u.access(), null).status());
    }

    @Test
    void history_isIsolatedPerUser_andClearOnlyAffectsCaller() {
        TestUser a = newUser();
        TestUser b = newUser();
        long artist = newArtist();
        long s1 = newSong(artist, "A-song");
        long s2 = newSong(artist, "B-song");
        play(a, s1);
        play(b, s2);

        assertEquals(List.of("A-song"), history(a));
        assertEquals(List.of("B-song"), history(b));

        assertEquals(204, delete("/api/recently-played", a.access()).status());
        assertEquals(204, delete("/api/recently-played", a.access()).status()); // rỗng vẫn OK
        assertEquals(0, history(a).size());
        assertEquals(List.of("B-song"), history(b));
    }

    @Test
    void deleteSong_removesItFromHistory() {
        TestUser admin = newAdmin();
        TestUser u = newUser();
        long artist = newArtist();
        long keep = newSong(artist, "Keep");
        long gone = newSong(artist, "Gone");
        play(u, keep);
        play(u, gone);

        assertEquals(204, delete("/api/admin/songs/" + gone, admin.access()).status());
        assertEquals(List.of("Keep"), history(u));
    }

    @Test
    void history_limitedTo30_andCleanupJobTrimsTable() {
        TestUser u = newUser();
        long artist = newArtist();
        List<Long> songs = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            songs.add(newSong(artist, "Song-" + i));
        }
        for (long s : songs) {
            play(u, s);
        }

        // GET luôn giới hạn 30 bài mới nhất dù bảng đang có 31 dòng
        List<String> h = history(u);
        assertEquals(31, rows(u.id()));
        assertEquals(30, h.size());
        assertEquals("Song-30", h.get(0));
        assertFalse(h.contains("Song-0"));

        // job dọn dẹp xóa dòng cũ nhất
        recentlyPlayedService.trimHistory();
        assertEquals(30, rows(u.id()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM recently_played WHERE user_id = ? AND song_id = ?",
                Long.class, u.id(), songs.get(0)));
        assertEquals(30, history(u).size());
    }
}