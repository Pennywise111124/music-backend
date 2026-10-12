# Lộ trình tiếp theo (Backend)

Trạng thái: **A (nối Android) và B (Docker) đã xong.** Phần còn lại là C (củng cố), D (tính năng mới), E (giao diện Android, xem repo Android).

---

## A. Nối app Android với backend: XONG

Đã nối đủ A1-A10: tầng mạng, đăng nhập, tự refresh token, duyệt, trình phát, Favorite/Follow, Playlist, màn Admin kèm upload, bỏ Room. Kết quả chi tiết ở `docs/PROGRESS.md` của repo Android (tag `v2.0-backend-integration`).

---

## B. Đóng gói và triển khai: XONG (mức phát triển)

Đã có `Dockerfile` hai giai đoạn, `docker-compose.yml` (app + `postgres:17-alpine` + volume `pgdata` và `uploads`), `.env.example`, healthcheck, profile `prod`, CI. **Đạt định nghĩa hoàn thành:** `docker compose up` dựng cả hệ thống từ máy trống, Flyway tự tạo schema, `requests.http` chạy được (bảng DK ở `docs/PROGRESS.md`).

Còn lại khi muốn **triển khai thật** (xem C): HTTPS, tài khoản DB riêng, sao lưu, quản lý bí mật.

---

## C. Củng cố backend (làm dần)

| Việc | Trạng thái | Giá trị | Ghi chú |
|---|---|---|---|
| Magic bytes cho file upload | **Xong** | Bảo mật | `FileSignature`; chỉ kiểm tra byte đầu |
| `accessDeniedHandler` JSON | **Xong** | Nhất quán | 403 luôn JSON |
| CI (GitHub Actions) | **Xong** | Chất lượng | Test + build image mỗi lần push |
| Thu hồi refresh token, rotate, endpoint logout | Chưa | Bảo mật | Bảng `refresh_tokens` (migration V3); app Android cần gọi logout và lưu refresh mới. Hiện token lộ dùng được tới 7 ngày |
| Thống nhất lỗi 401 (body JSON) | Chưa | Nhất quán | Thay `HttpStatusEntryPoint` bằng handler JSON; Android đang đọc body rỗng thành "Server error (401)" |
| Rate limiting (đăng nhập, upload) | Chưa | Bảo mật | |
| Phân trang Artist/Album | Chưa | Hiệu năng | Android đang tải toàn bộ; các API khác (bài hát, favorite, playlist, người dùng) đã có phân trang |
| Tìm kiếm không dấu tiếng Việt, collation | Chưa | Trải nghiệm | Extension `unaccent` của PostgreSQL |
| `StorageService` sang S3 hoặc Cloudinary | Chưa | Mở rộng | Chỉ cần thêm một implementation |
| Vai trò quản trị cấp dưới | Chưa | Mở rộng | Sửa `assertManageable`, thêm role vào enum và migration |
| Test tự động cho Genre, Album, Artist CRUD đầy đủ | Chưa | Độ phủ | Hiện phủ gián tiếp |
| `Instant`/`timestamptz` thay `LocalDateTime` | Chưa | Đúng đắn | Migration V3; hiện chốt hợp đồng UTC |
| Tài khoản DB không phải superuser, HTTPS qua reverse proxy, sao lưu `pgdata` và `uploads` | Chưa | Triển khai thật | Làm khi có môi trường thật |
| Cập nhật phụ thuộc tự động (Dependabot) | Chưa | Bảo trì | |
| CORS | Chưa | Chỉ khi có web client | |

---

## D. Tính năng mới (nếu muốn)

- **Endpoint app đang thiếu:** sửa hồ sơ (`PUT /api/users/me`), upload avatar, admin tạo và sửa người dùng. Hiện các màn tương ứng bị ẩn hoặc báo "chưa hỗ trợ".
- Danh sách phát đề xuất/thịnh hành, thống kê lượt nghe thật, tìm kiếm tổng hợp (song/artist/album một lần), thông báo khi artist theo dõi có bài mới.

---

## E. Giao diện Android

Xem `docs/ROADMAP.md` của repo Android (nhóm 1: loading, báo lỗi, làm mới; nhóm 2: chuẩn hóa theme; nhóm 3: thiết kế lại).

---

## Thứ tự đề xuất

1. Giao diện Android nhóm 1 (hết màn hình trống khi mạng chậm).
2. Thu hồi refresh token (bảo mật, đụng cả hai repo).
3. Thống nhất lỗi 401 và phân trang Artist/Album.
4. Các mục còn lại theo nhu cầu.