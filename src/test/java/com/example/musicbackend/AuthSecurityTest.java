package com.example.musicbackend;

import org.junit.jupiter.api.Test;
import com.example.musicbackend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class AuthSecurityTest extends IntegrationTestBase {

    // ---------- Đăng ký, đăng nhập ----------

    @Test
    void register_returnsTokensAndNeverLeaksPassword() {
        String username = unique("reg");
        Resp r = registerRaw(username);

        assertEquals(201, r.status());
        assertFalse(r.str("$.accessToken").isBlank());
        assertFalse(r.str("$.refreshToken").isBlank());
        assertEquals("USER", r.str("$.user.role"));
        assertFalse(r.body().toLowerCase().contains("password"));
    }

    @Test
    void register_duplicateUsernameOrEmail_conflict() {
        String username = unique("dup");
        assertEquals(201, registerRaw(username).status());
        assertEquals(409, registerRaw(username).status());
    }

    @Test
    void register_invalidInput_badRequest() {
        Resp r = post("/api/auth/register", null, """
                {"username":"ab","email":"nope","password":"1"}
                """);
        assertEquals(400, r.status());
    }

    @Autowired
    JwtUtil jwtUtil;

    @Test
    void register_usernameWithAt_badRequest() {
        Resp r = post("/api/auth/register", null,
                "{\"username\":\"co@dau\",\"email\":\"" + unique("at") + "@example.com\","
                        + "\"password\":\"" + PASSWORD + "\"}");
        assertEquals(400, r.status());
        assertTrue(r.str("$.errors.username").contains("@"));
    }

    @Test
    void login_byUsernameAndByEmail_ok_wrongPassword_unauthorized() {
        String username = unique("log");
        registerRaw(username);

        assertEquals(200, loginRaw(username, PASSWORD).status());
        assertEquals(200, loginRaw(username + "@example.com", PASSWORD).status());
        assertEquals(401, loginRaw(username, "wrong-password").status());
        assertEquals(401, loginRaw("khong-ton-tai-" + username, PASSWORD).status());
    }

    @Test
    void passwordIsStoredHashed() {
        String username = unique("hash");
        registerRaw(username);
        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE username = ?", String.class, username);
        assertNotEquals(PASSWORD, stored);
        assertTrue(stored.startsWith("$2"), "phải là BCrypt");
    }

    // ---------- Token ----------

    @Test
    void protectedEndpoint_withoutOrGarbageToken_unauthorized() {
        assertEquals(401, get("/api/genres", null).status());
        assertEquals(401, get("/api/genres", "token.rac.khong.hop.le").status());
    }

    @Test
    void accessToken_works() {
        TestUser u = newUser();
        assertEquals(200, get("/api/genres", u.access()).status());
    }

    @Test
    void refreshToken_cannotBeUsedAsBearer() {
        TestUser u = newUser();
        assertEquals(401, get("/api/genres", u.refresh()).status());
    }

    @Test
    void accessToken_cannotBeUsedToRefresh() {
        TestUser u = newUser();
        Resp r = post("/api/auth/refresh", null, "{\"refreshToken\":\"" + u.access() + "\"}");
        assertEquals(401, r.status());
    }

    @Test
    void refresh_returnsUsableNewAccessToken() {
        TestUser u = newUser();
        Resp r = post("/api/auth/refresh", null, "{\"refreshToken\":\"" + u.refresh() + "\"}");

        assertEquals(200, r.status());
        assertEquals(200, get("/api/genres", r.str("$.accessToken")).status());
    }

    @Test
    void refresh_garbageOrMissing() {
        assertEquals(401, post("/api/auth/refresh", null, "{\"refreshToken\":\"rac\"}").status());
        assertEquals(400, post("/api/auth/refresh", null, "{}").status());
    }

    @Test
    void token_worksEvenWhenUsernameEqualsAnotherUsersEmail() {
        TestUser u1 = newUser();
        TestUser u2 = newUser();

        String u1Email = jdbc.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, u1.id());

        jdbc.update("UPDATE users SET username = ? WHERE id = ?", u1Email, u2.id());

        String token = jwtUtil.generateAccessToken(u1Email);

        Resp r = post("/api/playlists", token, "{\"name\":\"Trung danh tinh\",\"isPublic\":true}");

        assertEquals(201, r.status());
        assertEquals(u2.id(), r.num("$.ownerId"));
    }

    // ---------- Phân quyền ----------

    @Test
    void adminEndpoints_userForbidden_anonymousUnauthorized_adminOk() {
        TestUser user = newUser();
        TestUser admin = newAdmin();

        assertEquals(403, get("/api/admin/users", user.access()).status());
        assertEquals(401, get("/api/admin/users", null).status());
        assertEquals(401, get("/api/admin/users", admin.refresh()).status());
        assertEquals(200, get("/api/admin/users", admin.access()).status());
    }

    @Test
    void adminUserList_neverLeaksPasswordHash() {
        TestUser admin = newAdmin();
        Resp r = get("/api/admin/users?size=50", admin.access());

        assertEquals(200, r.status());
        assertFalse(r.body().toLowerCase().contains("password"));
    }

    @Test
    void userCannotWriteGenresOrArtists() {
        TestUser user = newUser();
        assertEquals(403, post("/api/admin/genres", user.access(), "{\"name\":\"Hack\"}").status());
        assertEquals(403, post("/api/admin/artists", user.access(), "{\"name\":\"Hack\"}").status());
    }

    @Test
    void forbidden_hasJsonBody() {
        TestUser user = newUser();

        Resp r = get("/api/admin/users", user.access());

        assertEquals(403, r.status());
        assertEquals(403, r.num("$.status"));
        assertFalse(r.str("$.message").isBlank());
        assertFalse(r.str("$.timestamp").isBlank());
    }

    // ---------- Ban, xóa user ----------

    @Test
    void ban_takesEffectImmediately_andCanBeLifted() {
        TestUser admin = newAdmin();
        TestUser victim = newUser();

        assertEquals(200, get("/api/genres", victim.access()).status());

        Resp ban = put("/api/admin/users/" + victim.id() + "/ban", admin.access(), "{\"banned\":true}");
        assertEquals(200, ban.status());

        assertEquals(401, get("/api/genres", victim.access()).status());
        assertEquals(401, loginRaw(victim.username(), PASSWORD).status());
        assertEquals(401, post("/api/auth/refresh", null,
                "{\"refreshToken\":\"" + victim.refresh() + "\"}").status());

        assertEquals(200, put("/api/admin/users/" + victim.id() + "/ban", admin.access(),
                "{\"banned\":true}").status());

        assertEquals(200, put("/api/admin/users/" + victim.id() + "/ban", admin.access(),
                "{\"banned\":false}").status());
        assertEquals(200, loginRaw(victim.username(), PASSWORD).status());
        assertEquals(200, get("/api/genres", victim.access()).status());
    }

    @Test
    void ban_invalidBodyOrUnknownUser() {
        TestUser admin = newAdmin();
        assertEquals(400, put("/api/admin/users/1/ban", admin.access(), "{}").status());
        assertEquals(404, put("/api/admin/users/999999999/ban", admin.access(),
                "{\"banned\":true}").status());
    }

    @Test
    void admin_cannotBanOrDeleteSelfOrAnotherAdmin() {
        TestUser admin1 = newAdmin();
        TestUser admin2 = newAdmin();

        assertEquals(409, put("/api/admin/users/" + admin1.id() + "/ban", admin1.access(),
                "{\"banned\":true}").status());
        assertEquals(409, delete("/api/admin/users/" + admin1.id(), admin1.access()).status());
        assertEquals(409, put("/api/admin/users/" + admin2.id() + "/ban", admin1.access(),
                "{\"banned\":true}").status());
        assertEquals(409, delete("/api/admin/users/" + admin2.id(), admin1.access()).status());

        assertEquals(200, get("/api/admin/users", admin2.access()).status());
    }

    @Test
    void deleteUser_cleansEverythingAndReducesPopularity() {
        TestUser admin = newAdmin();
        TestUser victim = newUser();
        long artist = newArtist();
        long song = newSong(artist, unique("song-"));

        assertEquals(204, post("/api/artists/" + artist + "/follow", victim.access(), null).status());
        assertEquals(204, post("/api/favorites/" + song, victim.access(), null).status());
        assertEquals(204, post("/api/recently-played/" + song, victim.access(), null).status());
        Resp pl = post("/api/playlists", victim.access(), "{\"name\":\"Del\",\"isPublic\":true}");
        long playlistId = pl.num("$.id");
        assertEquals(204, post("/api/playlists/" + playlistId + "/songs/" + song,
                victim.access(), null).status());

        assertEquals(1, get("/api/artists/" + artist, admin.access()).num("$.popularity"));

        assertEquals(204, delete("/api/admin/users/" + victim.id(), admin.access()).status());

        assertEquals(401, get("/api/genres", victim.access()).status());
        assertEquals(401, loginRaw(victim.username(), PASSWORD).status());
        assertEquals(404, delete("/api/admin/users/" + victim.id(), admin.access()).status());

        assertEquals(0, get("/api/artists/" + artist, admin.access()).num("$.popularity"));
        assertEquals(0, count("playlists", "id", playlistId));
        assertEquals(0, count("playlist_songs", "playlist_id", playlistId));
        assertEquals(0, count("favorite_songs", "user_id", victim.id()));
        assertEquals(0, count("followed_artists", "user_id", victim.id()));
        assertEquals(0, count("recently_played", "user_id", victim.id()));
        // bài hát vẫn còn
        assertEquals(200, get("/api/songs/" + song, admin.access()).status());
    }

    private long count(String table, String column, long value) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, value);
    }
}