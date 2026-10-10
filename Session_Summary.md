# Session Summary — Music Backend (Project 2)

**Cập nhật lần cuối:** backend hoàn tất toàn bộ phạm vi thiết kế. 77 test tự động xanh, toàn bộ `requests.http` đã chạy lại và đạt sau đợt upload.
**File này dùng để mang sang cuộc trò chuyện mới.** Đọc kèm `music-backend-design.md` (thiết kế đã xây), `docs/PROGRESS.md`, `docs/ROADMAP.md`.

---

## 1. Bối cảnh

Đang thực hiện **Project 2** trong lộ trình học Backend Java/Spring Boot cá nhân (đã xong 24 bài lý thuyết, Phase 1-6). Mục tiêu: chuyển app Android **Music Player** (Kotlin, Jetpack Compose, MVVM, đang dùng Room local) sang có backend thật: **Spring Boot + PostgreSQL + JWT**, phân quyền User/Admin.

Repo app Android: `github.com/Danaai/MusicPlayer-Android-Kotlin` (đã đọc source thật).

**Backend đã xong.** Việc chính còn lại là **nối app Android với backend** (xem mục 10 và `docs/ROADMAP.md`).

---

## 2. Cách làm việc đã thống nhất (giữ nguyên)

- Ngôn ngữ: **tiếng Việt**.
- Người dùng thích **copy-paste trực tiếp từ chat**, không muốn tải file về rồi thêm vào. Mọi code/file viết sẵn trong code block.
- Học và làm từng phần nhỏ, thực hành ngay, review kỹ: **giải thích bản chất khi sai**, không chỉ đưa đáp án; chỉ ra pattern nếu lỗi lặp lại.
- Claude nhận lỗi thẳng thắn khi mình sai (đã xảy ra nhiều lần: đếm sai số test, suy đoán sai về `JwtAuthFilter`, hứa "ban hiệu lực ngay" khi chưa test).

**Quy trình mỗi module:**
1. Claude viết sẵn Entity, Repository, DTO, Controller, Exception, Config. **Service impl để TODO kèm hướng dẫn chi tiết trong Javadoc** cho người dùng tự viết.
2. Người dùng tự code phần TODO, dán lại để review.
3. Claude review: lỗi cú pháp, lỗi bản chất (giải thích tại sao), gom pattern lặp lại.
4. Test qua `requests.http` có 69 phần, 515 request, mỗi request có mã và kết quả kỳ vọng; sau này thêm test tự động.
5. Xong mới chuyển module tiếp theo.
6. **Khi test lệch, kiểm tra thứ tự chạy request và dữ liệu cũ trước khi nghi code.**
7. Để chắc test thật sự bảo vệ code: **phá code cố ý** rồi xem test có fail đúng chỗ không.

---

## 3. Setup thực tế

- Maven, Java 21, Spring Boot 4.1.1, PostgreSQL (DB `musicapp`), package `com.example.musicbackend`. Thư mục dự án: `D:\java_backend\music-backend` (Windows, PowerShell, VS Code).
- Biến môi trường: `JWT_SECRET` (chuỗi thô >= 32 ký tự, **bắt buộc**), `DB_PASSWORD` (mặc định `postgres`), `DB_URL` (mặc định `jdbc:postgresql://localhost:5432/musicapp`).
```powershell
  $env:JWT_SECRET = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
```
- Chạy: `./mvnw spring-boot:run`. Test tự động: `./mvnw test` (cần **Docker Desktop** đang chạy; đã cài).
- Test thủ công: `requests.http` (VS Code, extension **REST Client**), thư mục `test-files/` cho test upload.
- Tài khoản test: `testuser1` (ADMIN, nâng bằng SQL), `testuser2` (USER), mật khẩu `123456`. Register chỉ tạo USER; nâng admin: `UPDATE users SET role='ADMIN' WHERE username='testuser1';` rồi login lại.
- Flyway: V1 = schema (dump từ DB do Hibernate tạo, đã bỏ `OWNER TO`), V2 = unique index `LOWER(name)` cho genres. DB dev cũ dùng `baseline-on-migrate` (baseline-version 1).

---

## 4. Trạng thái các module

| Module | Trạng thái | Test thủ công | Test tự động |
|---|---|---|---|
| Auth (register/login/refresh, claim `type`) | Xong | A, TK, RF (đạt) | AuthSecurityTest (18) |
| Genre | Xong | GN1-30 | (trong SongTest) |
| Artist (+ ảnh upload) | Xong | AR1-33 | FollowTest, UploadTest |
| Album (+ ảnh upload) | Xong | AL0a-AL44 | SongTest, UploadTest |
| Song (multipart upload) | Xong | SG0a-SG67 | SongTest (9), UploadTest (15) |
| Favorite | Xong | FV0a-FV35 | FavoriteTest (7) |
| Follow + popularity | Xong | FL0a-FL33 | FollowTest (9) |
| Recently Played | Xong | RP0a-RP27 | RecentlyPlayedTest (6) |
| Playlist (+ ảnh) | Xong | PL0a-PL77 | PlaylistTest (13) |
| Admin User (ban, xóa) | Xong | US0a-US43 (+US11b, US34b) | AuthSecurityTest |
| File upload / phục vụ file | Xong | UP0a-UP67 | UploadTest (15) |
| Flyway | Xong | | chạy trong mọi test |

**Tổng test tự động: 77** (18 + 13 + 9 + 6 + 7 + 9 + 15). Danh sách endpoint đầy đủ nằm ở `music-backend-design.md` mục 5.

---

## 5. Quyết định thiết kế quan trọng

- Token: **access 15 phút + refresh 7 ngày**, claim `type`; refresh **không rotate** (quyết định có chủ đích). Filter nạp user từ DB mỗi request nên ban có hiệu lực ngay.
- `popularity` của artist = số lượt follow, lưu sẵn, cập nhật atomic (`ON CONFLICT DO NOTHING` + `UPDATE` trong cùng transaction). Admin không nhập.
- PUT là **thay thế toàn bộ** (field thiếu bị về null/false).
- Playlist private của người khác: **404** (giấu sự tồn tại); public mà không phải owner khi sửa: **403**. Admin không có đặc quyền với playlist của người khác.
- Admin không ban/xóa chính mình hoặc admin khác (409). Quy tắc gom ở `assertManageable` để sau này mở rộng vai trò quản trị cấp dưới.
- Mọi thao tác thêm/bỏ là idempotent. Ban nhận `{banned: true|false}` (không toggle).
- Upload: tên file UUID do server sinh, URL lưu **đường dẫn tương đối** `/api/files/...`; dọn file theo transaction (`afterRollback`/`afterCommit`); JSON không còn nhận `imageUrl`/`audioUrl`.
- Mọi endpoint (trừ `/api/auth/**`, `/api/files/**`) cần đăng nhập, kể cả đọc genre/song.
- Ngôn ngữ message: lỗi nghiệp vụ tiếng Anh; validation của Register/Login tiếng Việt; các module khác tiếng Anh.

---

## 6. Quy ước code

- Body lỗi qua `buildResponse`: `{timestamp, status, message}` hoặc `{timestamp, status, errors{field: msg}}`. 401 do thiếu/sai token: body rỗng; 401 do login/refresh: có JSON (login luôn là `Invalid credentials`, kể cả khi bị ban; refresh có 3 thông báo riêng, xem design mục 5); 403 do role: định dạng mặc định Spring; 403 do playlist: có JSON message
- Mọi query danh sách có tiêu chí phụ `IdAsc`/`id` để thứ tự ổn định khi trùng (quan trọng cho phân trang).
- Quan hệ `@ManyToOne(fetch = LAZY)` kèm `@EntityGraph` hoặc `join fetch` để tránh N+1.
- Entity khóa kép: `@EmbeddedId` + `@MapsId`. Xóa hàng loạt bằng `@Modifying @Query` lọc theo cả userId.
- `PageResponse<T>` dùng chung cho mọi API phân trang; tham số page/size luôn được kẹp.
- Không gọi `save()` trong update khi entity đang được quản lý (dirty checking).
- Không sửa migration Flyway đã chạy; đổi entity thì thêm `V3__...sql`.
- Mã test dùng tiền tố riêng mỗi module: A, TK, RF, GN, AR, AL, SG, FV, FL, RP, PL, US, UP.

---

## 7. Bug và bài học (tránh lặp lại)

1. `OncePerRequestFilter` nằm ở `org.springframework.web.filter`.
2. Spring Security bản mới: `new DaoAuthenticationProvider(userDetailsService)`.
3. Bắt `AuthenticationException` (class cha), không bắt hẹp `BadCredentialsException`, nếu không user bị ban nhận 500.
4. JWT secret luôn là raw UTF-8 bytes, **không** auto-detect Base64 (từng gây `WeakKeyException` âm thầm).
5. Thêm `HttpStatusEntryPoint(401)` làm lộ lỗi ẩn: 403/400 sinh từ `sendError` đi qua `/error` bị trả 401. Đã sửa bằng `.requestMatchers("/error").permitAll()`. **Sau mỗi lần đổi `SecurityConfig`, chạy lại toàn bộ test phân quyền.**
6. Cần handler cho `MethodArgumentTypeMismatchException` và `HttpMessageNotReadableException` (400).
7. JSON thiếu `}` vẫn parse được; test JSON hỏng dùng body `khong phai json`.
8. Ban phải được kiểm tra ở **filter** (`isEnabled()`), `refresh()` (`isBanned()`) và login, không chỉ ở login.
9. `logger` của `OncePerRequestFilter` là Commons Logging: dùng `logger.error("msg", e)`, không dùng `{}`.
10. Khi đổi hợp đồng API (JSON sang multipart), rà lại mọi request chuẩn bị ở các bộ test cũ.
11. File đã lưu phải gắn với kết quả transaction; **tìm entity trước khi lưu file**.
12. Tomcat chặn `%2F` và `//` trước khi vào code (400 HTML mặc định). An toàn, không cần sửa.
13. Access token không được dùng như refresh token (đã sửa bằng claim `type`).
14. `pg_dump` sinh `OWNER TO postgres` và `\restrict`, phải bỏ khỏi V1. Trên PowerShell dùng `-f`, không dùng `>` (ra UTF-16). Spring Boot 4 cần `spring-boot-starter-flyway` (chỉ `flyway-core` thì không chạy migration).
15. Testcontainers 2.x: `org.testcontainers.postgresql.PostgreSQLContainer`. Spring Boot 4 đã đổi package của MockMvc và `TestRestTemplate`, nên test dùng `java.net.http.HttpClient` thật.
16. **Chỉ kiểm tra mã trạng thái là chưa đủ** khi có lưới an toàn (`DataIntegrityViolationException` thành 409): phải kiểm tra cả thông báo mới phân biệt được check của ứng dụng với ràng buộc DB.
17. Race thật (Favorite thêm trùng) chỉ lộ khi test đồng thời; `INSERT ... ON CONFLICT DO NOTHING` là cách sửa. Điều kiện `popularity > 0` chỉ có tác dụng khi dữ liệu đã lệch, cần test cố tình làm lệch.
18. Spring Security tự thêm `X-Content-Type-Options: nosniff`, nên phá thử dòng header trong `FileController` vẫn xanh. Vẫn giữ dòng đó để không phụ thuộc cấu hình mặc định.

---

## 8. Kết quả kiểm chứng bằng phá code cố ý

Mọi lần phá đều có test fail đúng chỗ: bỏ `isEnabled()` (ban), đổi loại token ở filter, bỏ khóa dòng playlist (`[0,1,1,1,1,1]`), đảo thứ tự dọn dữ liệu khi xóa user, bỏ điều kiện `inserted == 1`/`deleted == 1`, bỏ `popularity > 0`, bỏ dọn follow khi xóa artist, đổi giới hạn lịch sử, bỏ kiểm tra album-artist, đổi sort favorite, bỏ `afterRollback`/`afterCommit` ở các luồng file, bỏ kiểm tra Content-Type, bỏ dọn ảnh playlist khi xóa user. Chi tiết ở `docs/PROGRESS.md`.

---

## 9. Việc còn treo (theo ưu tiên)

1. **Nối app Android với backend** (mục tiêu ban đầu, xem `docs/ROADMAP.md`).
2. Đóng gói Docker (`Dockerfile`, `docker-compose`: app + Postgres + volume `uploads`).
3. Kiểm tra nội dung file thật (magic bytes), không chỉ đuôi và Content-Type.
4. `accessDeniedHandler` để lỗi 403 cùng định dạng JSON với các lỗi khác.
5. Thu hồi refresh token (bảng lưu refresh token, rotate), endpoint logout phía server.
6. Phân trang cho `GET /api/artists` và `/api/albums`; tìm kiếm không dấu cho tiếng Việt; kiểm tra collation khi sắp xếp.
7. Chuyển `StorageService` sang S3 hoặc Cloudinary khi cần.
8. Rate limiting, cấu hình CORS (nếu có web client), quản lý secret khi triển khai thật.
9. Vai trò quản trị viên cấp dưới (sửa `assertManageable`).

---

## 10. Bước tiếp theo đề xuất

**Nối app Android**, theo thứ tự: (1) tầng mạng + đăng nhập (Retrofit, interceptor gắn token, authenticator tự refresh khi 401), (2) màn duyệt Song/Artist/Album/Genre, (3) trình phát với URL từ server, (4) Favorite/Follow/Recently Played, (5) Playlist, (6) màn Admin và upload, (7) bỏ Room. Chi tiết ở `docs/ROADMAP.md`.

---

## 11. Khi mở cuộc trò chuyện mới, gửi kèm

1. `Session_Summary.md` (file này).
2. `music-backend-design.md`.
3. `docs/PROGRESS.md` và `docs/ROADMAP.md` (nếu muốn Claude nắm đầy đủ lịch sử và kế hoạch).
4. **Zip (không dùng rar) thư mục `src`**, cộng `pom.xml`, `application.yml`. Nếu làm phần Android, gửi thêm các file Kotlin liên quan (Repository interface, Entity, DAO, ViewModel).

## 12. Ghi chú môi trường làm việc

- Claude **không có mạng** để `git clone` và không tự duyệt sâu GitHub; muốn đọc code thật phải được upload trực tiếp (zip).
- Claude không xem được code thật trên máy người dùng; mọi review dựa trên file được dán lên. Không suy đoán nội dung file chưa thấy.
- File `.rar` không giải nén được; luôn dùng `.zip`.