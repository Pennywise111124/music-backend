package com.example.musicbackend;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class TimeZoneTest extends IntegrationTestBase {

    @Test
    void configureTimeZone_setsUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            // Đặt về một múi giờ khác UTC trước, để test không "xanh giả" trên máy CI vốn
            // đã là UTC
            // hoặc khi IntegrationTestBase đã gọi hàm này từ trước.
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

            MusicBackendApplication.configureTimeZone();

            assertEquals("UTC", TimeZone.getDefault().getID());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void jpaTimestamps_areUtc() {
        TestUser user = newUser();
        Resp r = post("/api/playlists", user.access(), "{\"name\":\"TZ jpa\",\"isPublic\":true}");
        assertEquals(201, r.status());

        LocalDateTime createdAt = jdbc.queryForObject(
                "SELECT created_at FROM playlists WHERE id = ?", LocalDateTime.class, r.num("$.id"));

        assertNearUtcNow(createdAt);
    }

    @Test
    void nativeTimestamps_areUtc() {
        TestUser user = newUser();
        long song = newSong(newArtist(), unique("tz-song-"));

        // Đường CURRENT_TIMESTAMP của SQL thuần
        assertEquals(204, post("/api/favorites/" + song, user.access(), null).status());

        LocalDateTime addedAt = jdbc.queryForObject(
                "SELECT added_at FROM favorite_songs WHERE user_id = ? AND song_id = ?",
                LocalDateTime.class, user.id(), song);

        assertNearUtcNow(addedAt);
    }

    private static void assertNearUtcNow(LocalDateTime actual) {
        assertNotNull(actual);
        long diff = Math.abs(Duration.between(actual, LocalDateTime.now(ZoneOffset.UTC)).getSeconds());
        assertTrue(diff < 120, "Lệch " + diff + " giây so với UTC hiện tại: " + actual);
    }
}