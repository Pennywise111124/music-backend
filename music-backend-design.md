# Tài liệu thiết kế Backend — Music Player App (bản "as-built")

**Chuyển từ Room (local-only) sang Spring Boot + PostgreSQL + JWT**
Trạng thái: backend hoàn chỉnh theo phạm vi thiết kế, 77 test tự động xanh. Tài liệu này mô tả hệ thống **như đã xây**, không phải kế hoạch ban đầu. Chỗ nào khác thiết kế gốc đều được ghi ở mục 12.

---

## 1. Bối cảnh và phạm vi

App Android (Kotlin, Jetpack Compose, MVVM) trước đây lưu toàn bộ dữ liệu bằng Room. Backend này thay tầng lưu trữ đó bằng server nhiều người dùng, giữ nguyên nghiệp vụ và nâng cấp các điểm không phù hợp khi lên client-server (mật khẩu, đăng nhập, phân quyền).

Repo app gốc: `github.com/Danaai/MusicPlayer-Android-Kotlin` (đã đọc source thật, không chỉ README).

### Tính năng (đối chiếu từ code Android thật)

| Tính năng | Nguồn Android | Backend |
|---|---|---|
| Đăng ký, đăng nhập (username hoặc email) | `AuthRepositoryImpl`, `UserDao` | Có, JWT |
| Duyệt, tìm, phát bài hát | `SongDao` | Có, phân trang |
| Artist, Album, Genre | các `*Dao` | Có (Genre tách bảng riêng) |
| Favorite | `FavoriteSongEntity` | Có |
| Follow Artist | `FollowedArtistEntity` | Có, kèm `popularity` tự tính |
| Recently Played (tối đa 30) | `RecentlyPlayedEntity`, `trimHistory` | Có |
| Playlist public/private, sắp xếp bài | `PlaylistEntity`, `position` | Có |
| Admin quản lý Song/Artist/Album/Genre/User, ban | `ui/admin/*` | Có |

### Điểm bắt buộc đã đổi so với app cũ

- **Mật khẩu plaintext thành BCrypt.** Bản Android so sánh `password = :password` trực tiếp trong SQL.
- **Cột `isLoggedIn` trong DB thành JWT stateless.** Server không lưu phiên.
- Giữ nguyên logic đăng nhập bằng username hoặc email.

---

## 2. Công nghệ và cấu hình

| Hạng mục | Giá trị |
|---|---|
| Build | Maven (`./mvnw`) |
| Java | 21 |
| Spring Boot | 4.1.1 |
| Database | PostgreSQL (DB `musicapp`) |
| Migration | Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`) |
| Bảo mật | Spring Security, JWT (jjwt), BCrypt |
| Khác | Spring Data JPA, Validation, Lombok |
| Test | JUnit 5, Testcontainers (`testcontainers-postgresql`), `spring-boot-starter-test` |
| Package gốc | `com.example.musicbackend` |

### Biến môi trường

| Biến | Bắt buộc | Mặc định | Ghi chú |
|---|---|---|---|
| `JWT_SECRET` | Có | không | Chuỗi thô (UTF-8) tối thiểu 32 ký tự. **Không** phải Base64 |
| `DB_PASSWORD` | Không | `postgres` | |
| `DB_URL` | Không | `jdbc:postgresql://localhost:5432/musicapp` | Dùng để trỏ sang DB khác |

### `application.yml` (các khóa quan trọng)

```yaml
server.port: 8080
spring.jpa.hibernate.ddl-auto: validate        # Flyway quản lý schema
spring.flyway: { enabled: true, locations: classpath:db/migration,
                 baseline-on-migrate: true, baseline-version: 1 }
spring.servlet.multipart: { max-file-size: 20MB, max-request-size: 25MB }
jwt.secret: ${JWT_SECRET}
jwt.access-token-expiration-ms: 900000          # 15 phút
jwt.refresh-token-expiration-ms: 604800000      # 7 ngày
app.storage.upload-dir: uploads
app.recently-played.max-history: 30             # mặc định trong code
app.recently-played.cleanup-cron: "0 0 3 * * *" # mặc định trong code
```

---

## 3. Kiến trúc và cấu trúc thư mục

Phân lớp: Controller, Service (interface + impl), Repository (Spring Data JPA), Entity. DTO tách riêng khỏi entity. Lỗi xử lý tập trung ở `GlobalExceptionHandler`.

```
src/main/java/com/example/musicbackend/
  MusicBackendApplication.java
  config/        SecurityConfig, SchedulingConfig
  controller/    AuthController, GenreController, AdminGenreController,
                 ArtistController, FollowController, AdminArtistController,
                 AlbumController, AdminAlbumController,
                 SongController, AdminSongController,
                 FavoriteController, RecentlyPlayedController,
                 PlaylistController, AdminUserController, FileController
  dto/request/   Register, Login, Refresh, Genre, Artist, Album, Song,
                 Playlist, Reorder, Ban
  dto/response/  Auth, User, AdminUser, Genre, Artist, Album, Song,
                 Playlist, PlaylistDetail, PageResponse<T>
  entity/        User, Role, Genre, Artist, Album, Song, FavoriteSong(+Id),
                 FollowedArtist(+Id), RecentlyPlayed(+Id), Playlist,
                 PlaylistSong(+Id)
  exception/     GlobalExceptionHandler + các exception nghiệp vụ
  mapper/        SongMapper, ArtistMapper, PlaylistMapper
  repository/    một repository cho mỗi entity (+ PlaylistSongCount projection)
  security/      JwtUtil, JwtAuthFilter, UserDetailsServiceImpl, CurrentUserProvider
  service/       interface cho từng domain
  service/impl/  cài đặt tương ứng (+ AdminUserServiceImpl)
  storage/       FileCategory, StorageService, LocalStorageServiceImpl, TxCallbacks
src/main/resources/
  application.yml
  db/migration/  V1__init_schema.sql, V2__genre_name_unique_ci.sql
src/test/java/com/example/musicbackend/
  IntegrationTestBase, AuthSecurityTest, PlaylistTest, FollowTest,
  RecentlyPlayedTest, FavoriteTest, SongTest, UploadTest
requests.http    (test thủ công bằng REST Client, 69 phần)
test-files/      (file mẫu cho test upload; big.mp3 nằm trong .gitignore)
uploads/         (file người dùng tải lên; nằm trong .gitignore)
```

---

## 4. Database (PostgreSQL, quản lý bằng Flyway)

Schema thật nằm ở `V1__init_schema.sql` (dump từ DB do Hibernate tạo, đã bỏ các dòng `OWNER TO`). `V2` thêm unique index không phân biệt hoa/thường cho genre. **Không bao giờ sửa migration đã chạy**; muốn đổi schema thì thêm `V3__...sql`.

| Bảng | Cột chính | Khóa, ràng buộc |
|---|---|---|
| `users` | id, username, email, password_hash (BCrypt), role, is_banned, avatar_url, bio, created_at | PK id; UNIQUE username, email; CHECK role IN (USER, ADMIN) |
| `artists` | id, name, image_url, info, popularity | PK id. `popularity` = số lượt follow, lưu sẵn |
| `genres` | id, name | PK id; UNIQUE name; UNIQUE INDEX `LOWER(name)` (V2) |
| `albums` | id, title, artist_id, release_date, image_url | FK artist_id (bắt buộc) |
| `songs` | id, title, artist_id, album_id, genre_id, duration_ms, audio_url, image_url, release_date, created_at | FK artist (bắt buộc), album, genre (tùy chọn); index trên 3 FK |
| `favorite_songs` | user_id, song_id, added_at | PK (song_id, user_id) |
| `followed_artists` | user_id, artist_id, followed_at | PK (artist_id, user_id) |
| `recently_played` | user_id, song_id, played_at | PK (song_id, user_id); nghe lại thì cập nhật `played_at` |
| `playlists` | id, name, description, owner_id, image_url, is_public (mặc định false), created_at | FK owner_id |
| `playlist_songs` | playlist_id, song_id, position, added_at | PK (playlist_id, song_id) |

Ghi chú:
- Không có `ON DELETE CASCADE`. Việc dọn dữ liệu liên quan do tầng service làm (mục 9).
- `songCount` và `ownerName` của playlist **không lưu**, tính động khi truy vấn.
- Tên constraint do Hibernate sinh ngẫu nhiên (`ukpe1a9...`, `fk12ct...`). Hoạt động bình thường, chỉ khó đọc.
- `image_url` của `artists`, `albums` giới hạn 255 ký tự, của `songs`, `playlists` 500. URL lưu dạng đường dẫn tương đối nên đủ dùng.

---

## 5. API

### Quy ước chung

- Base URL dev: `http://localhost:8080` (trên emulator Android: `http://10.0.2.2:8080`).
- Header: `Authorization: Bearer <accessToken>`.
- **Mọi endpoint đều cần đăng nhập**, trừ `/api/auth/**` và `/api/files/**`. Kể cả `GET /api/genres`, `GET /api/songs`. (Bản thiết kế gốc ghi "Public/User", bản xây thật yêu cầu đăng nhập.)
- Nhóm `/api/admin/**` yêu cầu role `ADMIN`.
- **PUT là thay thế toàn bộ**: field không gửi sẽ bị đặt về `null` (hoặc `false`). Client phải gửi đủ các field của resource.
- Mọi URL file trong response (`audioUrl`, `imageUrl`) là **đường dẫn tương đối** dạng `/api/files/audio/<uuid>.mp3`. Client tự ghép với base URL.
- Phân trang: `?page=0&size=20`. `page` âm thành 0, `size` kẹp vào [1, 50].
- Mọi thao tác "thêm/bỏ" (favorite, follow, thêm/bớt bài playlist, xóa lịch sử) là **idempotent**: gọi lặp lại vẫn thành công.

### Mã trạng thái và định dạng lỗi

| Mã | Khi nào |
|---|---|
| 200, 201, 204 | Thành công (204 cho thao tác không có nội dung trả về) |
| 400 | Validation sai, JSON hỏng, id sai kiểu, thiếu part multipart, file sai loại/rỗng, thứ tự reorder sai, album không thuộc artist |
| 401 | Thiếu/sai/hết hạn token, dùng nhầm loại token, tài khoản bị ban hoặc đã bị xóa, sai mật khẩu |
| 403 | Không đủ quyền (role không phải ADMIN; hoặc playlist **public** của người khác khi sửa) |
| 404 | Không tìm thấy; **playlist private của người khác luôn trả 404** |
| 409 | Trùng dữ liệu, đang được sử dụng, admin tự xử lý mình/admin khác, đổi artist của album còn bài hát |
| 413 | File vượt giới hạn (audio 20MB, ảnh 5MB) |
| 415 | Sai Content-Type (JSON gửi vào endpoint multipart, part `metadata` không khai `application/json`) |

Định dạng body lỗi:

```json
{ "timestamp": "2026-10-03T17:41:51", "status": 409, "message": "..." }
{ "timestamp": "...", "status": 400, "errors": { "title": "Title is required" } }
```

Ngoại lệ:
- Lỗi **401 do thiếu, sai hoặc hết hạn token** (qua filter) có body rỗng. Lỗi **401 do login/refresh** (`Invalid credentials`, `Refresh token is invalid or expired`, `Account is banned!`) vẫn có body JSON `{timestamp, status, message}`.
- Lỗi **403** có hai dạng: (a) do Spring Security (không đủ role ADMIN) dùng định dạng mặc định (`timestamp`, `status`, `error`, `path`, không có `message`); (b) do nghiệp vụ playlist (`PlaylistAccessDeniedException`) có JSON `{timestamp, status, message}`.
- Thông báo lỗi nghiệp vụ của Auth bằng tiếng Anh; thông báo validation của Register/Login bằng tiếng Việt (`RefreshRequest` bằng tiếng Anh); các module khác tiếng Anh.

### Danh sách endpoint

**Auth** (công khai)

| Method | Đường dẫn | Body | Kết quả |
|---|---|---|---|
| POST | `/api/auth/register` | `{username (3-30), email, password (>=6)}` | 201 `AuthResponse`; 409 trùng username/email |
| POST | `/api/auth/login` | `{usernameOrEmail, password}` | 200 `AuthResponse`; 401 |
| POST | `/api/auth/refresh` | `{refreshToken}` | 200 `AuthResponse` (access mới, refresh giữ nguyên); 401 |

Không có endpoint logout: client xóa token. Refresh token **không bị thu hồi** phía server trước khi hết hạn (xem mục 12).

**Genre**

| Method | Đường dẫn | Quyền |
|---|---|---|
| GET | `/api/genres`, `/api/genres/{id}` | User |
| POST, PUT, DELETE | `/api/admin/genres[/{id}]` | Admin (body `{name}` tối đa 100 ký tự) |

**Artist**

| Method | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/artists?query=` | Danh sách theo tên A-Z, có `followed` |
| GET | `/api/artists/top?limit=` | `popularity` giảm dần; `limit` kẹp [1, 50], mặc định 10 |
| GET | `/api/artists/{id}` | |
| GET | `/api/artists/followed?page&size` | Mới follow trước; `PageResponse<ArtistResponse>` |
| POST, DELETE | `/api/artists/{id}/follow` | 204, idempotent. Follow artist không tồn tại: 404; bỏ follow: luôn 204 |
| POST, PUT, DELETE | `/api/admin/artists[/{id}]` | Admin. Body `{name, info}`. `popularity` **không nhận** |
| PUT | `/api/admin/artists/{id}/image` | Admin, multipart part `image` |

**Album**

| Method | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/albums?query=` | Theo title A-Z |
| GET | `/api/albums/by-artist/{artistId}` | Mới nhất trước, album không có ngày xếp cuối; artist không tồn tại: 404 |
| GET | `/api/albums/{id}` | |
| POST, PUT, DELETE | `/api/admin/albums[/{id}]` | Body `{title, artistId, releaseDate}`. `artistId` bắt buộc |
| PUT | `/api/admin/albums/{id}/image` | multipart part `image` |

**Song**

| Method | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/songs?query=&page&size` | `PageResponse<SongResponse>`, sắp theo title rồi id |
| GET | `/api/songs/by-artist/{id}`, `/by-album/{id}`, `/{id}` | `List<SongResponse>`; id không tồn tại: 404 |
| POST | `/api/admin/songs` | **multipart**: part `metadata` (`application/json`), `audio` (bắt buộc), `cover` (tùy chọn) |
| PUT | `/api/admin/songs/{id}` | JSON, **chỉ metadata**; gửi kèm `audioUrl` sẽ bị bỏ qua |
| PUT | `/api/admin/songs/{id}/audio`, `/cover` | multipart part `audio` / `cover` |
| DELETE | `/api/admin/songs/{id}` | |

`metadata` của Song: `{title (<=255), artistId (bắt buộc), albumId?, genreId?, durationMs (>0), releaseDate? "yyyy-MM-dd"}`. `albumId` nếu có phải thuộc đúng `artistId`.

**Favorite**: `GET /api/favorites?page&size` (mới thêm trước, `PageResponse<SongResponse>`); `POST|DELETE /api/favorites/{songId}` (204).

**Recently Played**: `GET /api/recently-played` (`List<SongResponse>`, tối đa 30, mới nghe trước); `POST /api/recently-played/{songId}` (204, nghe lại thì lên đầu); `DELETE /api/recently-played` (204, chỉ xóa của người gọi).

**Playlist**

| Method | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/playlists/me?page&size` | Playlist của tôi (public lẫn private), mới tạo trước |
| GET | `/api/playlists/public?query=&page&size` | Duyệt playlist công khai |
| GET | `/api/playlists/{id}` | `PlaylistDetailResponse` kèm `songs` theo `position` |
| POST | `/api/playlists` | Body `{name, description?, isPublic?}`; owner lấy từ token |
| PUT, DELETE | `/api/playlists/{id}` | Chỉ owner |
| PUT | `/api/playlists/{id}/image` | multipart `image`, chỉ owner |
| POST, DELETE | `/api/playlists/{id}/songs/{songId}` | 204, chỉ owner; thêm vào cuối, bớt thì dồn lại vị trí |
| PUT | `/api/playlists/{id}/reorder` | Body `{songIds: [...]}` phải khớp **chính xác** tập bài hiện có, sai thì 400 |

**Admin User** (`ROLE_ADMIN`)

| Method | Đường dẫn | Ghi chú |
|---|---|---|
| GET | `/api/admin/users?query=&page&size` | Tìm theo username hoặc email; không bao giờ trả mật khẩu |
| PUT | `/api/admin/users/{id}/ban` | Body `{banned: true|false}` (không phải toggle) |
| DELETE | `/api/admin/users/{id}` | Xóa kèm toàn bộ dữ liệu của user |

**Files** (công khai, không cần token): `GET /api/files/{audio|images}/{uuid}.{ext}`. Hỗ trợ header `Range` (206) để tua bài.

### Hình dạng response

```jsonc
// AuthResponse
{ "accessToken": "...", "refreshToken": "...", "tokenType": "Bearer",
  "user": { "id": 3, "username": "...", "email": "...", "avatarUrl": null, "bio": null, "role": "USER" } }

// SongResponse
{ "id": 1, "title": "...", "artistId": 1, "artistName": "...",
  "albumId": null, "albumTitle": null, "genreId": null, "genreName": null,
  "durationMs": 215000, "audioUrl": "/api/files/audio/<uuid>.mp3",
  "imageUrl": null, "releaseDate": "2024-06-05", "createdAt": "2026-10-01T19:13:37" }

// ArtistResponse   (followed: user hiện tại có đang theo dõi không;
//                   luôn false trong response của POST/PUT /api/admin/artists)
{ "id": 1, "name": "...", "imageUrl": null, "info": null, "popularity": 0, "followed": false }

// AlbumResponse
{ "id": 1, "title": "...", "artistId": 1, "artistName": "...", "releaseDate": null, "imageUrl": null }

// PlaylistResponse (PlaylistDetailResponse thêm "songs": [SongResponse])
{ "id": 1, "name": "...", "description": null, "imageUrl": null, "isPublic": false,
  "ownerId": 3, "ownerName": "...", "songCount": 0, "createdAt": "..." }

// PageResponse<T>
{ "content": [...], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }

// AdminUserResponse
{ "id": 1, "username": "...", "email": "...", "avatarUrl": null, "bio": null,
  "role": "USER", "banned": false, "createdAt": "..." }
```

---

## 6. Xác thực và phân quyền

- **JWT HS256**, key = bytes UTF-8 thô của `JWT_SECRET`. Mỗi token có claim `type`:
  - `access` (15 phút): dùng cho mọi API.
  - `refresh` (7 ngày): chỉ dùng ở `/api/auth/refresh`. Không rotate (refresh token giữ nguyên khi đổi access token).
  - Dùng sai loại đều bị 401.
- **`JwtAuthFilter`** nạp user từ DB ở **mỗi request** và kiểm tra `isEnabled()`. Vì vậy ban có hiệu lực ngay với token đang sống, và user đã bị xóa nhận 401 (không phải 500). `refresh()` cũng chặn tài khoản bị ban.
- **Quy tắc đường dẫn** (`SecurityConfig`):
  - `/api/auth/**`, `/api/files/**`, `/error`: công khai.
  - `/api/admin/**`: `hasRole("ADMIN")`.
  - Còn lại: `authenticated()`.
  - Không token: `HttpStatusEntryPoint(401)`. Có `@EnableMethodSecurity`.
- Role tạo ra khi đăng ký luôn là `USER`. Nâng lên `ADMIN` bằng SQL: `UPDATE users SET role='ADMIN' WHERE username='...'`. Quyền đọc từ DB mỗi request nên có hiệu lực ngay.
- **Kiểm tra quyền sở hữu ở tầng service** (không chỉ theo role). Admin **không** có đặc quyền với playlist của người khác.

### Quy tắc playlist

| Tình huống | Kết quả |
|---|---|
| Xem playlist private của người khác (kể cả admin) | 404 (giấu sự tồn tại) |
| Xem playlist public của người khác | 200 |
| Sửa/xóa/thêm/bớt/reorder/đổi ảnh, không phải owner, playlist public | 403 |
| Như trên, playlist private | 404 |

### Quy tắc quản trị user

- Admin không được ban hay xóa **chính mình** hoặc **admin khác** (409). Toàn bộ quy tắc nằm trong `AdminUserServiceImpl.assertManageable`. Muốn thêm vai trò quản trị cấp dưới thì chỉ sửa chỗ này.

---

## 7. Nghiệp vụ chính

- **Follow và popularity.** `popularity` là cột đếm lưu sẵn, cập nhật atomic:
  - Follow dùng `INSERT ... ON CONFLICT DO NOTHING`, chỉ khi chèn được 1 dòng mới `UPDATE popularity = popularity + 1`.
  - Bỏ follow chỉ giảm khi xóa được 1 dòng, và có điều kiện `popularity > 0`.
  - Cả hai bước nằm trong cùng một transaction. Admin không nhập tay `popularity`.
- **Favorite.** Thêm bằng `INSERT ... ON CONFLICT DO NOTHING` (chống race thêm trùng).
- **Recently Played.** Ghi bằng upsert `ON CONFLICT DO UPDATE SET played_at`. `GET` luôn `LIMIT` 30 (đúng dù bảng lớn hơn). Job `@Scheduled` (3 giờ sáng) dọn dòng cũ bằng `ROW_NUMBER() OVER (PARTITION BY user_id ...)`.
- **Playlist.**
  - Thao tác sửa danh sách bài khóa dòng playlist bằng `@Lock(PESSIMISTIC_WRITE)` để không gán trùng `position` khi nhiều request đồng thời.
  - `reorder` validate xong mới sửa, nên request sai không làm đổi dữ liệu nửa chừng.
  - Danh sách playlist đếm số bài bằng một query `GROUP BY` cho cả trang (tránh N+1).
- **Song.** `artistId` bắt buộc; `albumId`, `genreId` tùy chọn nhưng nếu gửi phải tồn tại (404). Album phải thuộc đúng artist của bài (400). Album còn bài hát không được đổi artist (409).
- **Ràng buộc "đang được dùng"** (409): xóa Genre/Album/Artist khi còn bài hát dùng; xóa Artist khi còn album.

---

## 8. Lưu trữ file

- Interface `StorageService` (Strategy Pattern). Cài đặt hiện tại: `LocalStorageServiceImpl`, lưu vào `uploads/audio/` và `uploads/images/`. Muốn chuyển S3 hoặc Cloudinary chỉ cần thêm một implementation.
- **Tên file do server sinh** (`<uuid>.<ext>`), không bao giờ dùng tên client gửi (chặn path traversal và trùng tên). Phục vụ file kiểm tra tên bằng regex UUID và `startsWith` thư mục.
- Kiểm tra: đuôi, Content-Type, kích thước, file rỗng.

| Loại | Đuôi | Tối đa |
|---|---|---|
| audio | mp3, m4a, wav, ogg | 20MB |
| ảnh | jpg, jpeg, png, webp | 5MB |

- Phục vụ file với Content-Type suy ra từ đuôi (không tin client), `X-Content-Type-Options: nosniff`, cache 30 ngày (tên UUID không bao giờ bị ghi đè).
- **Dọn file gắn với transaction** (`TxCallbacks`):
  - File vừa lưu: xóa nếu transaction **rollback** (`afterRollback`).
  - File cũ: chỉ xóa sau khi **commit** (`afterCommit`).
  - Luôn tìm entity **trước** khi lưu file, để request sai không để lại file mồ côi.
- JSON request của Artist/Album/Playlist/Song **không nhận** `imageUrl`/`audioUrl`. Chỉ upload mới đổi được URL file.

---

## 9. Dọn dữ liệu dây chuyền

Không có `ON DELETE CASCADE`, nên service dọn thủ công theo thứ tự (bảng con trước, bảng cha sau), trong một `@Transactional`:

| Khi xóa | Dọn |
|---|---|
| Song | favorite_songs, recently_played, playlist_songs của bài; file audio và cover (sau commit) |
| Artist | followed_artists; file ảnh (sau commit). Chặn nếu còn album hoặc bài hát |
| Album | file ảnh (sau commit). Chặn nếu còn bài hát |
| Genre | Chặn nếu còn bài hát |
| Playlist | playlist_songs; file ảnh |
| User | giảm `popularity` các artist đang follow (**trước** khi xóa follow), followed_artists, favorite_songs, recently_played, playlist_songs của playlist user, playlists, user; ảnh playlist (sau commit) |

---

## 10. Xử lý lỗi

`GlobalExceptionHandler` map exception sang mã trạng thái. Có lưới an toàn `DataIntegrityViolationException` thành 409 (kèm `log.warn`), phòng race hiếm khi hai request xóa/chèn xen kẽ. Cũng có handler cho `MethodArgumentTypeMismatchException` (400), `HttpMessageNotReadableException` (400), `MaxUploadSizeExceededException` (413), `MissingServletRequestPartException` (400), `HttpMediaTypeNotSupportedException` (415).

---

## 11. Kiểm thử

- **Tự động:** `./mvnw test` (cần Docker Desktop đang chạy). Testcontainers dựng Postgres sạch, Flyway chạy V1 và V2, `ddl-auto: validate` xác nhận entity khớp schema. Test gọi API thật qua `HttpClient` trên cổng ngẫu nhiên, mỗi test tự tạo user và dữ liệu tên ngẫu nhiên. **77 test**, gồm cả ca đồng thời (race): thêm bài playlist, follow, favorite.
- **Thủ công:** `requests.http` (VS Code, extension REST Client), 69 phần, mỗi test có mã (A, TK, RF, GN, AR, AL, SG, FV, FL, RP, PL, US, UP) và kết quả kỳ vọng.
- Các bảo đảm quan trọng đều đã được kiểm chứng bằng cách **phá code cố ý** để thấy test fail (xem `docs/PROGRESS.md`).

---

## 12. Khác biệt so với thiết kế ban đầu

| Chủ đề | Thiết kế gốc | Bản đã xây | Lý do |
|---|---|---|---|
| Token | 1 JWT, logout phía client | access (15 phút) + refresh (7 ngày), claim `type` | Phiên dài mà vẫn hạn chế rủi ro lộ token |
| `popularity` | admin nhập tay | tự tính từ lượt follow | Phản ánh đúng ý nghĩa, khó sai lệch |
| Toggle ban | `PUT .../ban` (toggle) | nhận `{banned: true|false}` | Idempotent, an toàn khi client retry |
| Quyền đọc | Genre/Song "Public/User" | mọi endpoint (trừ auth, files) cần đăng nhập | Đơn giản và an toàn hơn |
| Upload | `StorageService` ở bước cuối | multipart, tên UUID, dọn file theo transaction | Chống path traversal, không để file mồ côi |
| Recently Played dọn 30 | scheduled hoặc `LIMIT` | cả hai: `LIMIT` khi đọc, job dọn bảng | Luôn đúng, bảng không phình |
| Schema | `ddl-auto: update` | Flyway + `validate` | Schema có phiên bản, test được |
| Danh sách Artist/Album | không nói rõ | trả toàn bộ (chưa phân trang) | Dữ liệu ít, để dành làm sau |
| Genre trùng tên | không nói rõ | không phân biệt hoa/thường (ứng dụng + unique index `LOWER(name)`) | Tránh "Pop" và "pop" |

---

## 13. Giới hạn đã biết

- Refresh token **không thu hồi được** trước hạn và không rotate. Muốn thu hồi cần bảng lưu refresh token.
- Chưa có endpoint logout phía server, chưa có rate limiting, chưa cấu hình CORS (app Android native không cần, web client thì cần).
- Kiểm tra file chỉ dựa trên đuôi và Content-Type, chưa kiểm tra nội dung thật (magic bytes).
- Response 403 do Spring Security không cùng định dạng JSON với các lỗi khác.
- Sắp xếp theo `title` phụ thuộc collation của PostgreSQL (tiếng Việt có dấu có thể không đúng như mong đợi).
- Tìm kiếm không bỏ dấu tiếng Việt.
- File lưu local trên server (chưa có object storage).
- `GET /api/artists` và `/api/albums` chưa phân trang.