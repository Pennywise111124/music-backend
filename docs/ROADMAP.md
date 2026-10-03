# Lộ trình tiếp theo

Thứ tự đề xuất: A (nối Android) trước, vì đó là mục tiêu ban đầu của dự án. B, C làm song song hoặc sau tùy nhu cầu.

---

## A. Nối app Android với backend (ưu tiên 1)

**Mục tiêu:** app Android dùng backend thay Room; đăng nhập thật, dữ liệu dùng chung, nhiều thiết bị.

### Nguyên tắc

- Giữ **interface Repository** hiện có; chỉ đổi phần cài đặt từ Room/DAO sang gọi API. ViewModel và UI thay đổi ít nhất.
- Làm từng nhóm tính năng nhỏ, chạy được ngay (đúng quy trình cũ): Claude viết sẵn khung (API interface, DTO, mapper), người dùng tự viết phần logic Repository.
- **Cần gửi code Android thật** (Repository interface, Entity, DAO, ViewModel liên quan). Claude không suy đoán.

### Các bước

| # | Việc | Chi tiết cần chú ý |
|---|---|---|
| A1 | Tầng mạng | Retrofit + OkHttp + JSON converter. Base URL: emulator `http://10.0.2.2:8080/`, máy thật dùng IP LAN. Dev cần cho phép cleartext HTTP (`usesCleartextTraffic` hoặc network security config) |
| A2 | Lưu token | DataStore hoặc EncryptedSharedPreferences. Thay cột `isLoggedIn` bằng "có token hợp lệ" |
| A3 | Đăng ký/đăng nhập | `POST /api/auth/register`, `/login` (đăng nhập bằng username hoặc email). Lỗi: đọc `message` hoặc `errors{field}` từ body |
| A4 | Interceptor + Authenticator | Interceptor gắn `Authorization: Bearer`. Khi gặp **401**: gọi `/api/auth/refresh` bằng refresh token (đồng bộ, chỉ một lần cùng lúc), thử lại request một lần; refresh cũng 401 thì đăng xuất. Lưu ý 401 còn xảy ra khi tài khoản bị ban/xóa |
| A5 | Duyệt dữ liệu | Song (có phân trang, cân nhắc Paging 3), Artist, Album, Genre. Ghép base URL với `imageUrl`/`audioUrl` tương đối (Coil cho ảnh) |
| A6 | Trình phát | Media3/ExoPlayer phát thẳng URL `/api/files/audio/...` (không cần token, hỗ trợ Range để tua). Gọi `POST /api/recently-played/{songId}` khi bắt đầu phát |
| A7 | Favorite, Follow | Idempotent nên an toàn khi thử lại; có thể cập nhật UI lạc quan. `ArtistResponse.followed` cho biết trạng thái |
| A8 | Playlist | `/me`, `/public`, chi tiết kèm `songs`, thêm/bớt/reorder; hiểu 404 (playlist private của người khác) và 403 (playlist public, không phải owner) |
| A9 | Màn Admin | Quản lý song/artist/album/genre/user. Tạo bài hát bằng multipart (`@Multipart`: part `metadata` là `RequestBody` với `application/json`, `audio`, `cover`). Hiển thị theo `role` trong `AuthResponse.user` |
| A10 | Dọn Room | Bỏ Room hoặc giữ làm cache offline (tùy chọn) |

### Sự khác biệt Room và backend cần xử lý trong ánh xạ

- `songs.genre` (text tự do) thành `genreId` + `genreName`.
- `PlaylistWithDetails` thành `PlaylistResponse` (`ownerName`, `songCount` do server tính).
- Lịch sử nghe: server giữ tối đa 30 bài, không cần `trimHistory` phía client.
- `isLoggedIn` biến mất; trạng thái đăng nhập là có token.
- Thời gian dùng ISO-8601 (`yyyy-MM-dd`, `LocalDateTime`); hiển thị `dd/MM/yyyy` do client định dạng.
- PUT là thay thế toàn bộ: màn sửa phải gửi đủ field.

### Định nghĩa hoàn thành

Đăng nhập, duyệt, phát nhạc, favorite/follow, playlist, lịch sử nghe chạy hoàn toàn qua server trên emulator; token hết hạn tự refresh; màn admin thao tác được; không còn phụ thuộc Room cho dữ liệu nghiệp vụ.

---

## B. Đóng gói và triển khai (ưu tiên 2)

- `Dockerfile` cho backend (JDK 21).
- `docker-compose.yml`: app + PostgreSQL + volume cho `uploads/` và dữ liệu DB.
- Biến môi trường qua file `.env` (không commit): `JWT_SECRET`, `DB_PASSWORD`, `DB_URL`.
- Healthcheck; log gọn hơn (tắt `show-sql` ở profile production).
- Cân nhắc profile `prod`: HTTPS (reverse proxy), CORS nếu có web, secret quản lý an toàn.

**Hoàn thành khi:** `docker compose up` dựng cả hệ thống từ máy trống, Flyway tự tạo schema, `requests.http` chạy được.

---

## C. Củng cố backend (làm dần)

| Việc | Giá trị | Ghi chú |
|---|---|---|
| Thu hồi refresh token, rotate, endpoint logout | Bảo mật | Cần bảng `refresh_tokens` (migration V3) |
| Magic bytes cho file upload | Bảo mật | Kiểm tra nội dung thật, không chỉ đuôi và Content-Type |
| `accessDeniedHandler` JSON | Nhất quán | Để 403 cùng định dạng với lỗi khác |
| Rate limiting (đăng nhập, upload) | Bảo mật | |
| Phân trang Artist/Album | Hiệu năng | Dữ liệu lớn |
| Tìm kiếm không dấu tiếng Việt, collation khi sắp xếp | Trải nghiệm | Có thể dùng extension `unaccent` của PostgreSQL |
| `StorageService` sang S3 hoặc Cloudinary | Mở rộng | Chỉ cần thêm một implementation |
| Vai trò quản trị cấp dưới | Mở rộng | Sửa `assertManageable`, thêm role vào enum và migration |
| Test tự động cho Genre, Album, Artist CRUD đầy đủ | Độ phủ | Hiện được phủ gián tiếp qua các test khác |
| CI (GitHub Actions chạy `./mvnw test`) | Chất lượng | Runner có sẵn Docker |

---

## D. Tính năng mới (nếu muốn)

Danh sách phát đề xuất/thịnh hành, thống kê lượt nghe thật, tìm kiếm tổng hợp (song/artist/album một lần), upload avatar người dùng, hồ sơ người dùng, thông báo khi artist theo dõi có bài mới.