# Session Summary — Music Player (Backend và Android)

**Cập nhật lần cuối:** 12/10/2026. Hai hướng chính đã xong: backend Spring Boot (93 test tự động, Docker, CI xanh) và app Android đã nối backend (15 test đơn vị, đã bỏ Room). Việc còn lại nằm ở mục 9.
**File này dùng để mang sang cuộc trò chuyện mới.** Đọc kèm: `music-backend-design.md`, `docs/PROGRESS.md`, `docs/ROADMAP.md` (backend) và `docs/ARCHITECTURE.md`, `docs/PROGRESS.md`, `docs/ROADMAP.md` của repo Android.

---

## 1. Bối cảnh và hai repo

Đây là **Project 2** trong lộ trình học Backend Java/Spring Boot cá nhân (đã xong 24 bài lý thuyết, Phase 1-6): chuyển app Android **Music Player** (Kotlin, Jetpack Compose, MVVM, trước đây dùng Room local) sang backend thật **Spring Boot + PostgreSQL + JWT**, phân quyền User/Admin.

| | Backend | Android |
|---|---|---|
| Repo | `github.com/Pennywise111124/music-backend` | `github.com/Danaai/MusicPlayer-Android-Kotlin` |
| Nhánh | `main` | `master` |
| Tag | `v1.0-backend-complete`, `v1.1-docker-ci` | `v2.0-backend-integration` |
| Thư mục máy | `D:\java_backend\music-backend` | `D:\java_backend\MusicPlayer-Android-Kotlin` |
| Tài khoản GitHub | Pennywise111124 | Danaai (là tài khoản khác của cùng một người) |
| Giấy phép | MIT | MIT |

Lưu ý hai tài khoản: remote của repo Android là `https://Danaai@github.com/Danaai/...` (có `Danaai@`) để Git Credential Manager dùng đúng đăng nhập; `git config user.name/user.email` của repo Android là `Danaai` và email noreply của tài khoản đó.

---

## 2. Cách làm việc đã thống nhất (giữ nguyên)

- Ngôn ngữ: **tiếng Việt** (README, tài liệu, commit theo kiểu `loại: mô tả`).
- Người dùng thích **copy-paste trực tiếp từ chat**. Mọi code, file, lệnh viết sẵn trong code block; các chuỗi lệnh (add, commit, push, tag) đưa **thành một khối liền**, không tách rời.
- Làm từng phần nhỏ, thực hành ngay, review kỹ: **giải thích bản chất khi sai**; chỉ ra pattern nếu lỗi lặp lại.
- **Chỉ kết luận khi có bằng chứng** (log, kết quả test, code thật). Không suy đoán nội dung file chưa thấy. Phân biệt rõ "đã xác nhận" và "suy luận, chưa chạy".
- Claude nhận lỗi thẳng thắn. Các lỗi đã xảy ra: đếm sai số test, suy đoán sai về `JwtAuthFilter`, hứa "ban hiệu lực ngay" khi chưa test, mô tả sai kết quả test mất mạng (T4: tưởng ném `IOException`, thực tế trả 401 gốc), nói "10 chỗ `File(...)`" trong khi chỉ 5 chỗ cần đổi ở A5, dự đoán sai tác dụng của thứ tự `_currentSong = null` trong `stopAndReset`, hướng dẫn `git add -p` giả định các đoạn luôn tách được, và tách lệnh `push`/`tag` khiến tag trỏ nhầm commit cũ.

**Quy trình mỗi phần:**
1. Claude viết sẵn khung (Entity, DTO, Controller, Config; hoặc API interface, DTO, mapper). **Phần logic chính để TODO kèm hướng dẫn** cho người dùng tự viết.
2. Người dùng viết, dán lại để review (lỗi cú pháp, lỗi bản chất, gom pattern lặp).
3. Test thủ công theo bảng có kết quả kỳ vọng, rồi test tự động.
4. **Phá code cố ý** để thấy test fail đúng chỗ; ghi lại kết quả (dự đoán sai cũng là thông tin quý).
5. Khi test lệch, kiểm tra thứ tự chạy và dữ liệu cũ trước khi nghi code.
6. Trước khi commit: kiểm tra an toàn (không có bí mật, không có đường dẫn máy), build và test xanh, `git status` sạch.

---

## 3. Setup thực tế và lệnh thường dùng

- Windows, PowerShell. Backend: VS Code hoặc Antigravity. Android: Android Studio Rabbit 1 | 2026.2.1, JDK 25.0.3 (Gradle JVM), emulator API 37.2.
- Tài khoản test: `testuser1` (ADMIN, nâng bằng SQL), `testuser2` (USER), mật khẩu `123456`.

**Backend, chạy bằng Docker** (khuyên dùng):
```powershell
cd D:\java_backend\music-backend
docker compose up -d --build --wait
docker compose exec db psql -U postgres -d musicapp -c "UPDATE users SET role='ADMIN' WHERE username='testuser1';"
docker compose down        # giữ dữ liệu; thêm -v để XÓA dữ liệu và file upload
```
**Backend, chạy trực tiếp:** đặt `JWT_SECRET` (chuỗi thô >= 32 ký tự, bắt buộc), `./mvnw spring-boot:run`. **Test:** `./mvnw test` (cần Docker Desktop đang chạy). Chi tiết ở `README.md`.

**Android:**
```powershell
cd D:\java_backend\MusicPlayer-Android-Kotlin
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```
App chạy trên emulator gọi `http://10.0.2.2:8080/` (đặt bằng `buildConfigField BASE_URL`). Đổi `BASE_URL` cho máy thật.

**Git (mỗi repo riêng):** kiểm tra `git status --short`, `git log --oneline` trước khi push. `git push` chỉ gửi **commit**, không gửi file. Sau khi tạo tag, kiểm tra `git rev-parse HEAD` và `git rev-parse "<tag>^{commit}"` phải trùng nhau.

---

## 4. Trạng thái

### Backend (11 module)

| Module | Trạng thái | Test thủ công | Test tự động |
|---|---|---|---|
| Auth (register/login/refresh, claim `type`) | Xong | A, TK, RF | AuthSecurityTest |
| Genre | Xong | GN1-30 | (trong SongTest) |
| Artist (+ ảnh upload) | Xong | AR1-33 | FollowTest, UploadTest |
| Album (+ ảnh upload) | Xong | AL0a-AL44 | SongTest, UploadTest |
| Song (multipart upload) | Xong | SG0a-SG67 | SongTest, UploadTest |
| Favorite | Xong | FV0a-FV35 | FavoriteTest |
| Follow + popularity | Xong | FL0a-FL33 | FollowTest |
| Recently Played | Xong | RP0a-RP27 | RecentlyPlayedTest |
| Playlist (+ ảnh) | Xong | PL0a-PL77 | PlaylistTest |
| Admin User (ban, xóa) | Xong | US0a-US43 | AuthSecurityTest |
| File upload, phục vụ file, chữ ký file | Xong | UP0a-UP67 | UploadTest, FileSignatureTest |
| Flyway | Xong | | chạy trong mọi test |
| Vận hành (health, 403 JSON) | Xong | OP1-OP3 | AuthSecurityTest |
| Thời gian UTC | Xong | | TimeZoneTest |
| Docker, CI | Xong | DK1-DK9 (xem PROGRESS) | CI chạy cả bộ test |

**Tổng test tự động: 93** (AuthSecurityTest 23, PlaylistTest 13, UploadTest 17, FollowTest 9, SongTest 9, FavoriteTest 7, RecentlyPlayedTest 6, FileSignatureTest 6, TimeZoneTest 3). Đối chiếu với `target/surefire-reports` khi cập nhật. `requests.http`: **70 phần, 518 request**.

### Android

| Bước | Nội dung | Trạng thái |
|---|---|---|
| A1-A2 | Tầng mạng, lưu token (DataStore), `SessionManager`, `AppContainer` | Xong |
| A3 | Đăng ký, đăng nhập (username hoặc email) | Xong (L1-L11) |
| A4 | `TokenAuthenticator` + test MockWebServer | Xong (8 test) |
| A5 | Duyệt Song/Artist/Album, phân trang, tìm kiếm | Xong (B1-B8) |
| A6 | Trình phát, Recently Played, dừng nhạc khi đăng xuất | Xong (P1-P9, O1-O6) |
| A7 | Favorite, Follow | Xong (F1-F12) |
| A8 | Playlist | Xong (PL1-PL14) |
| A9 | Màn Admin, upload, quản lý người dùng | Xong (AD1-AD14, U1-U13, AU1-AU12) |
| A10 | Bỏ Room, Hilt, KSP | Xong (R1-R10) |
| Tích hợp trên Docker | S1-S15 | Đạt |
| Múi giờ UTC | server và app | Xong |
| Test Android | 15 test đơn vị | Xanh |

---

## 5. Quyết định thiết kế quan trọng

**Backend**
- Token: **access 15 phút + refresh 7 ngày**, claim `type`; refresh **không rotate** (có chủ đích). Filter nạp user từ DB mỗi request nên ban có hiệu lực ngay; filter tra user bằng `findByUsername` (subject của token là username). Username không chứa `@` hoặc khoảng trắng.
- `popularity` = số lượt follow, lưu sẵn, cập nhật atomic. Admin không nhập.
- PUT là **thay thế toàn bộ**. Mọi thao tác thêm/bỏ idempotent. Ban nhận `{banned: true|false}`.
- Playlist private của người khác: 404; public mà không phải owner khi sửa: 403. Admin không có đặc quyền với playlist của người khác. Admin không ban/xóa chính mình hoặc admin khác (409, `assertManageable`).
- Upload: tên file UUID do server sinh, URL tương đối `/api/files/...`; dọn file theo transaction; **kiểm tra chữ ký (byte đầu) và khớp đuôi**; JSON không nhận `imageUrl`/`audioUrl`.
- Mọi endpoint (trừ `/api/auth/**`, `/api/files/**`, `/actuator/health`) cần đăng nhập. Lỗi 403 luôn JSON `{timestamp, status, message}`; lỗi 401 do thiếu/sai token vẫn body rỗng.
- Mọi thời gian server là **UTC** (hợp đồng đặt trong `MusicBackendApplication.configureTimeZone()`; cột `timestamp` không múi giờ).

**Android**
- Giữ interface `MusicRepository`/`UserRepository`; thay phần cài đặt bằng `RemoteMusicRepository`/`RemoteUserRepository`. Chỉ đổi interface ở chỗ lộ kiểu của Room (`Set<Long>` cho favorite/follow) và bỏ `addSongToAlbum`.
- Không dùng Hilt; `AppContainer` (singleton, tạo trong `MusicApp`) giữ client mạng, phiên và repository. Factory lấy từ container.
- Luồng **đọc** không bao giờ ném lỗi (lỗi thì giữ cache hoặc trả rỗng); luồng **ghi**: `insert/update` ném lỗi kèm thông báo, `delete` nuốt lỗi; thông báo lỗi hiện qua Toast bằng kênh `UiMessages`.
- Cache trong repository khóa theo **người dùng**; favorite/follow lạc quan có hoàn tác; ảnh/nhạc dùng URL tuyệt đối do mapper ghép; ngày giờ server đọc theo UTC.
- Backend chưa có endpoint tạo/sửa người dùng, sửa hồ sơ: các màn này bị ẩn hoặc báo "chưa hỗ trợ".

---

## 6. Quy ước code

**Backend**
- Body lỗi qua `buildResponse`: `{timestamp, status, message}` hoặc `{timestamp, status, errors{field: msg}}`. 401 do thiếu/sai token: body rỗng. 401 do login/refresh: JSON (login luôn `Invalid credentials`, kể cả khi bị ban; refresh có 3 thông báo, xem design mục 5). 403: JSON `{timestamp, status, message}`.
- Mọi query danh sách có tiêu chí phụ `IdAsc`/`id`; quan hệ `@ManyToOne(LAZY)` kèm `@EntityGraph`/`join fetch`; entity khóa kép dùng `@EmbeddedId` + `@MapsId`; không gọi `save()` khi entity đang được quản lý; `PageResponse<T>` dùng chung, page/size luôn được kẹp.
- Không sửa migration Flyway đã chạy (đổi schema thì thêm `V3__...sql`). Sau mỗi lần đổi `SecurityConfig`, chạy lại toàn bộ test.
- Mã test thủ công có tiền tố mỗi module: A, TK, RF, GN, AR, AL, SG, FV, FL, RP, PL, US, UP, OP.

**Android**
- `RemoteMusicRepository`: helper `remoteFlow`/`remoteCall` (không ném), `remoteWrite` (ném + thông báo), `remoteAction` (nuốt + thông báo), `cachedFlow`, `userIdsFlow`, `optimisticToggle`, `uploadLocalFile`.
- Tải mọi trang: `PAGE_SIZE = 50` (server kẹp [1, 50]), `MAX_PAGES = 200`.
- Test dùng JUnit 4 + `mockwebserver3`; route của máy chủ giả khớp theo **tiền tố** nên đăng ký route dài hơn trước.

---

## 7. Bug và bài học (tránh lặp lại)

1. `OncePerRequestFilter` nằm ở `org.springframework.web.filter`.
2. Spring Security bản mới: `new DaoAuthenticationProvider(userDetailsService)`.
3. Bắt `AuthenticationException` (class cha), không bắt hẹp `BadCredentialsException`, nếu không user bị ban nhận 500.
4. JWT secret luôn là raw UTF-8 bytes, **không** auto-detect Base64.
5. `HttpStatusEntryPoint(401)` làm lộ lỗi ẩn: 403/400 sinh từ `sendError` đi qua `/error` bị trả 401; sửa bằng `.requestMatchers("/error").permitAll()`. **Sau mỗi lần đổi `SecurityConfig`, chạy lại toàn bộ test.**
6. Cần handler cho `MethodArgumentTypeMismatchException` và `HttpMessageNotReadableException` (400).
7. JSON thiếu `}` vẫn parse được; test JSON hỏng dùng body `khong phai json`.
8. Ban phải được kiểm tra ở **filter**, `refresh()` và login.
9. `logger` của `OncePerRequestFilter` là Commons Logging: `logger.error("msg", e)`, không dùng `{}`.
10. Khi đổi hợp đồng API (JSON sang multipart), rà lại mọi request ở các bộ test cũ.
11. File đã lưu phải gắn với kết quả transaction; **tìm entity trước khi lưu file**.
12. Tomcat chặn `%2F` và `//` trước khi vào code (400 HTML mặc định). An toàn.
13. Access token không được dùng như refresh token (claim `type`).
14. `pg_dump` sinh `OWNER TO postgres`/`\restrict`, phải bỏ khỏi V1. PowerShell dùng `-f`, không dùng `>` (ra UTF-16). Spring Boot 4 cần `spring-boot-starter-flyway`.
15. Testcontainers 2.x: `org.testcontainers.postgresql.PostgreSQLContainer`. Spring Boot 4 đã đổi package của MockMvc và `TestRestTemplate`, nên test dùng `java.net.http.HttpClient` thật.
16. **Chỉ kiểm tra mã trạng thái là chưa đủ** khi có lưới an toàn (`DataIntegrityViolationException` thành 409): kiểm tra cả thông báo.
17. Race thật chỉ lộ khi test đồng thời; `INSERT ... ON CONFLICT DO NOTHING` là cách sửa. Điều kiện `popularity > 0` chỉ có tác dụng khi dữ liệu đã lệch, cần test cố tình làm lệch.
18. Spring Security tự thêm `X-Content-Type-Options: nosniff`, nên phá thử dòng header trong `FileController` vẫn xanh.
19. `findByUsernameOrEmail` trả hai dòng khi username của người này trùng email người kia; token bị coi là chưa đăng nhập (401). Cấm `@` trong username và tra token bằng `findByUsername`.
20. Cấu hình ghi cứng (`url` trong `application.yml`) làm biến môi trường trong tài liệu vô tác dụng; kiểm tra bằng grep, không tin tài liệu.
21. **Thời gian**: bài học về hai đường ghi (`LocalDateTime.now()` theo JVM, `CURRENT_TIMESTAMP` theo phiên DB) và driver PostgreSQL dùng múi giờ JVM; chốt UTC bằng `TimeZone.setDefault` trong `main`. Kiểm chứng: phá code làm cả hai đường lệch đúng 25200 giây.
22. Kiểm tra magic bytes: đặt **sau** các kiểm tra rẻ (rỗng, kích thước, đuôi, Content-Type), **trước** khi ghi file; kiểm tra cả thông báo lỗi; cập nhật dữ liệu mẫu và README cùng lúc.
23. `stateIn(..., WhileSubscribed)`: `.value` đứng yên ở giá trị khởi tạo khi không có ai thu thập (nút Save "không làm gì"). Lấy người dùng bằng `first()` trong coroutine.
24. Authenticator OkHttp: 401 của `/api/auth/**` không được đi refresh; chỉ đăng xuất khi refresh trả 401, mất mạng thì giữ phiên; `synchronized` và so sánh token để chỉ refresh một lần; OkHttp tự giới hạn 20 lần theo dõi nhưng vẫn cần `responseCount`.
25. Ô nhập Compose phải cập nhật **đồng bộ**; kết quả tìm kiếm cập nhật sau, có `debounce`. Hiện kết quả qua luồng chậm làm chữ nhảy và mất dấu cách.
26. Luồng đọc từ mạng không được ném lỗi; `insert/update` ném, `delete` nuốt (UI gọi không có `try/catch`).
27. Cache dùng chung phải khóa theo `userId`; đặt cache về `null` làm các luồng đang thu thập đứng yên, hãy cập nhật tại chỗ.
28. Đuôi file lấy từ trình chọn không đáng tin (thường không có dấu chấm, bị đặt `.mp3`; ảnh luôn lưu `.jpg`): nhận dạng loại file bằng byte đầu trước khi upload.
29. Docker: biến shell ghi đè `.env`; đổi `DB_PASSWORD` sau khi volume tồn tại gây `password authentication failed`; build bằng image `maven` tránh CRLF và quyền thực thi của `mvnw`; viết `$${VAR}` trong healthcheck compose; DB không mở cổng ra ngoài.
30. Test JVM gọi code dùng `android.util.Log` cần `testOptions { unitTests.isReturnDefaultValues = true }`. OkHttp 5 dùng artifact `mockwebserver3` (`RecordedRequest.requestLine` để khớp route).
31. Thứ tự đặt `_currentSong = null` trong `stopAndReset()` hóa ra không ảnh hưởng kết quả (hook xóa `PlayerStateStorage` sau đó); vẫn giữ làm lớp bảo vệ. Dự đoán có thể sai; ghi lại kết quả thật.
32. Git: `git push` chỉ gửi commit; tag trỏ vào `HEAD` tại lúc tạo (kiểm tra bằng `rev-parse`); index có thể đã chứa thao tác xóa staged sẵn (commit chúng trước); các đoạn thay đổi nằm kề nhau không tách được bằng `git add -p`, khi đó commit cả file cùng việc đang làm; `skip-worktree` và `.git/info/exclude` chỉ có hiệu lực trên máy hiện tại; không commit nhạc có bản quyền.
33. PowerShell: `Set-Content -Encoding ascii` cho `.env`; `[IO.File]::WriteAllBytes("$PWD\...")` vì .NET không theo thư mục hiện tại của PowerShell; console Windows hiển thị méo tiếng Việt trong thông báo commit nhưng nội dung lưu đúng UTF-8 (xem `git log`).

---

## 8. Kết quả kiểm chứng bằng phá code cố ý

Mọi lần phá đều làm test fail đúng chỗ, trừ bốn trường hợp: (1) bỏ `popularity > 0` và (2) bỏ `existsByGenreId` lần đầu **không** bị bắt, đã được siết bằng test cố ý làm lệch dữ liệu và kiểm tra thông báo; (3) bỏ header `nosniff` vẫn xanh vì Spring Security tự thêm header đó. Ba trường hợp này nằm ở `docs/PROGRESS.md` của backend. (4) Dự đoán sai về thứ tự `_currentSong = null` trong `stopAndReset` (Android) nằm ở `docs/PROGRESS.md` của repo Android. Bảng chi tiết nằm ở `docs/PROGRESS.md` của từng repo.

---

## 9. Việc còn treo (theo ưu tiên gợi ý)

1. **Giao diện Android nhóm 1**: loading, báo lỗi đọc, kéo làm mới (xem `docs/ROADMAP.md` của Android).
2. **Thu hồi refresh token**, xoay vòng, endpoint logout phía server (migration V3 `refresh_tokens`, sửa cả app).
3. Thống nhất body JSON cho lỗi 401 do thiếu/sai token.
4. Rate limiting, CORS (nếu có web client), phân trang `GET /api/artists` và `/api/albums`, tìm kiếm không dấu tiếng Việt, collation.
5. Chuẩn hóa theme và component giao diện (nhóm 2), rồi thiết kế lại (nhóm 3).
6. Endpoint backend còn thiếu cho app: sửa hồ sơ, upload avatar, admin tạo/sửa người dùng (hiện các màn này bị ẩn hoặc báo chưa hỗ trợ).
7. CI cho Android; `CHANGELOG.md`, `docs/DEPLOYMENT.md`.
8. Triển khai thật: HTTPS qua reverse proxy, tài khoản DB không phải superuser, sao lưu volume `pgdata` và `uploads`, quản lý bí mật, cân nhắc object storage.
9. Vai trò quản trị cấp dưới (sửa `assertManageable`); `Instant`/`timestamptz` thay `LocalDateTime` (migration V3).

---

## 10. Bước tiếp theo đề xuất

Giao diện Android nhóm 1 (hết màn hình trống khi mạng chậm), rồi thu hồi refresh token. Nếu muốn nghiêng về backend: thống nhất lỗi 401, phân trang Artist/Album.

---

## 11. Khi mở cuộc trò chuyện mới, gửi kèm

1. `Session_Summary.md` (file này) và `music-backend-design.md`.
2. `docs/PROGRESS.md`, `docs/ROADMAP.md` (backend) và `docs/ARCHITECTURE.md`, `docs/PROGRESS.md`, `docs/ROADMAP.md` (Android) nếu muốn Claude nắm đầy đủ lịch sử.
3. **Zip (không dùng rar)**: backend gồm `src`, `pom.xml`, `Dockerfile`, `docker-compose.yml`; Android gồm `app/src`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`. Loại `.git`, `build/`, `target/`, `.env`, nhạc thật.

## 12. Ghi chú môi trường làm việc

- Claude **không có mạng** để `git clone`; công cụ đọc web bị GitHub chặn (`ROBOTS_DISALLOWED`), nên muốn đọc code thật phải được upload trực tiếp (zip) hoặc dán.
- Claude không xem được máy người dùng; mọi review dựa trên file được dán lên và log được dán.
- File `.rar` không giải nén được; luôn dùng `.zip`.