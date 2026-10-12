# Music Backend

![CI](https://github.com/Pennywise111124/music-backend/actions/workflows/ci.yml/badge.svg)

Backend cho app Android Music Player ([MusicPlayer-Android-Kotlin](https://github.com/Danaai/MusicPlayer-Android-Kotlin)). Công nghệ: Spring Boot 4.1.1, Java 21, PostgreSQL, JWT, Flyway. Chạy được bằng Docker Compose: chỉ cần tạo file `.env`, không cần cài JDK hay PostgreSQL.

Tài liệu: thiết kế như đã xây [`music-backend-design.md`](music-backend-design.md) · bàn giao và bài học [`Session_Summary.md`](Session_Summary.md) · tiến độ [`docs/PROGRESS.md`](docs/PROGRESS.md) · lộ trình [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Tính năng

- Đăng ký, đăng nhập bằng username hoặc email. JWT access 15 phút và refresh 7 ngày, mật khẩu BCrypt, phân quyền USER/ADMIN, ban người dùng có hiệu lực ngay.
- Bài hát, nghệ sĩ, album, thể loại: tìm kiếm, phân trang, upload nhạc và ảnh (kiểm tra loại file bằng nội dung), phát có hỗ trợ `Range`.
- Favorite, Follow (popularity tự tính), Recently Played (30 bài), Playlist công khai hoặc riêng tư.
- Quản trị: CRUD nội dung, quản lý người dùng.

## Yêu cầu

- Docker Desktop: để chạy bằng Docker và để chạy test tự động.
- JDK 21 và PostgreSQL (DB `musicapp`): chỉ khi chạy trực tiếp để phát triển.
- VS Code với extension REST Client: để chạy `requests.http`.

## Chạy bằng Docker (khuyên dùng)

Không cần cài JDK hay PostgreSQL.

1. Tạo file cấu hình (không commit, `.env` đã nằm trong `.gitignore`):
```powershell
Copy-Item .env.example .env
```
Mở `.env`, đặt `DB_PASSWORD` và một `JWT_SECRET` ngẫu nhiên từ 32 ký tự trở lên (chuỗi thô, không phải Base64). Ví dụ sinh secret:
```powershell
$secret = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
(Get-Content .env) -replace '^JWT_SECRET=.*', "JWT_SECRET=$secret" | Set-Content .env -Encoding ascii
```
2. Chạy (lần đầu build lâu):
```powershell
docker compose up -d --build
docker compose ps
```
Cả `app` và `db` phải ở trạng thái `healthy`.
3. API ở `http://localhost:8080` (emulator Android: `http://10.0.2.2:8080`). Kiểm tra sức khỏe: `http://localhost:8080/actuator/health`.
4. Tạo tài khoản (chạy A1, A2 trong `requests.http`) rồi nâng admin trong container DB:
```powershell
docker compose exec db psql -U postgres -d musicapp -c "UPDATE users SET role='ADMIN' WHERE username='testuser1';"
```
Sau đó login lại (TK1, TK2).
5. Dừng: `docker compose down` (giữ dữ liệu) hoặc `docker compose down -v` (**xóa** dữ liệu và file upload).

Ghi chú:
- Dữ liệu nằm ở volume `pgdata`, file upload ở volume `uploads`. Cổng DB không mở ra máy chủ.
- Compose bật profile `prod` (tắt log SQL). `JWT_SECRET` cố định trong `.env` nên token vẫn dùng được sau khi khởi động lại.
- Biến môi trường của shell **ghi đè** `.env`.
- Tắt `./mvnw spring-boot:run` trước khi chạy compose (cùng cổng 8080).

## Chạy trực tiếp để phát triển

1. Tạo database:
```sql
CREATE DATABASE musicapp;
```
2. Đặt biến môi trường (PowerShell, mỗi phiên terminal):
```powershell
$env:JWT_SECRET = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
# tùy chọn: $env:DB_PASSWORD = "..."   $env:DB_USER = "..."   $env:DB_URL = "jdbc:postgresql://localhost:5432/musicapp"
```
3. Chạy app (Flyway tự tạo schema trên DB trống):
```powershell
./mvnw spring-boot:run
```
4. Tạo tài khoản và nâng admin: chạy A1, A2 trong `requests.http`, rồi trong psql:
```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'testuser1';
```
Sau đó login lại (TK1, TK2).

## Biến môi trường

| Biến | Bắt buộc | Mặc định | Ghi chú |
|---|---|---|---|
| `JWT_SECRET` | Có | không | Chuỗi thô (UTF-8) tối thiểu 32 ký tự, **không** phải Base64 |
| `DB_PASSWORD` | Không | `postgres` | Trong Docker: bắt buộc đặt trong `.env` |
| `DB_USER` | Không | `postgres` | |
| `DB_URL` | Không | `jdbc:postgresql://localhost:5432/musicapp` | Docker đặt `jdbc:postgresql://db:5432/musicapp` |
| `SPRING_PROFILES_ACTIVE` | Không | (trống) | Docker đặt `prod`: tắt log SQL |

## Test

| Cách | Lệnh | Ghi chú |
|---|---|---|
| Tự động | `./mvnw test` | 93 test; Docker phải đang chạy; chạy riêng: `-Dtest=PlaylistTest`; báo cáo ở `target/surefire-reports` |
| Thủ công | mở `requests.http` | 70 phần, 518 request. Chạy từ trên xuống, chạy TK1, TK2 trước. Token hết hạn sau 15 phút |

**File mẫu cho test upload.** Server kiểm tra **byte đầu** của file (JPEG, PNG, WebP, MP3, M4A, WAV, OGG), nên file giả toàn chữ không còn qua được. Tạo thư mục `test-files/` với các file có chữ ký hợp lệ (chúng không nghe được; muốn nghe thử thì upload mp3 thật). `big.mp3` (22MB, nằm trong `.gitignore`) chỉ cần vượt giới hạn kích thước:
```powershell
mkdir test-files
$id3 = [byte[]](0x49,0x44,0x33,0x03,0,0,0,0,0,0)           # "ID3" + phần đầu thẻ
$png = [byte[]](0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A)   # chữ ký PNG
[IO.File]::WriteAllBytes("$PWD\test-files\a.mp3", [byte[]]($id3 + (1..32)))
[IO.File]::WriteAllBytes("$PWD\test-files\b.mp3", [byte[]]($id3 + (33..64)))
[IO.File]::WriteAllBytes("$PWD\test-files\pixel.png", [byte[]]($png + (1..16)))
[IO.File]::WriteAllBytes("$PWD\test-files\pixel2.png", [byte[]]($png + (17..32)))
"hello" | Set-Content test-files\notes.txt
"x" | Set-Content test-files\evil.exe
New-Item -ItemType File test-files\empty.mp3
$f = [IO.File]::Create("$PWD\test-files\big.mp3"); $f.SetLength(22MB); $f.Close()
```
(Dùng `$PWD\...` vì .NET không dùng thư mục hiện tại của PowerShell cho đường dẫn tương đối.)

Reset dữ liệu trước khi chạy lại toàn bộ `requests.http` (giữ nguyên bảng `users`), và xóa nội dung `uploads/audio`, `uploads/images`:
```sql
DELETE FROM followed_artists; DELETE FROM favorite_songs; DELETE FROM recently_played;
DELETE FROM playlist_songs; DELETE FROM playlists; DELETE FROM songs;
DELETE FROM albums; DELETE FROM artists; DELETE FROM genres;
```

## Migration (Flyway)

- `src/main/resources/db/migration/V1__init_schema.sql`, `V2__genre_name_unique_ci.sql`.
- **Không sửa migration đã chạy** (Flyway lưu checksum). Đổi schema thì thêm `V3__...sql`.
- `ddl-auto: validate`: sửa entity mà quên migration thì app không khởi động.

## CI

Workflow `.github/workflows/ci.yml` chạy mỗi lần push và pull request: chạy toàn bộ test (Testcontainers cần Docker, runner `ubuntu-latest` có sẵn) và kiểm tra `Dockerfile` build được. Khi test lỗi, báo cáo `surefire-reports` được lưu làm artifact.

## Lỗi thường gặp

| Triệu chứng | Nguyên nhân và cách xử lý |
|---|---|
| App không lên, báo thiếu `jwt.secret` | Chưa đặt `JWT_SECRET` trong terminal hiện tại |
| `WeakKeyException` | `JWT_SECRET` ngắn hơn 32 ký tự |
| Hàng loạt 401 sau khi sửa code xác thực | Token cũ không có claim `type`; login lại. Token hết hạn sau 15 phút |
| `Migration checksum mismatch` | Đã sửa migration đã chạy. Dựng lại DB dev hoặc hoàn tác file |
| Flyway không chạy, không có `flyway_schema_history` | Thiếu `spring-boot-starter-flyway` (chỉ có `flyway-core`) |
| Test báo `role "postgres" does not exist` | V1 còn dòng `OWNER TO postgres`; xóa các dòng đó |
| Test báo Docker không chạy | Mở Docker Desktop rồi chạy lại |
| `409` ở A1, A2 | Bình thường, user đã tồn tại |
| US0c, US0d ra 409 | `banme`, `delme` còn sót từ lần chạy dở; xóa tay trong DB |
| Test `requests.http` lệch kết quả | Kiểm tra dữ liệu cũ chưa reset và thứ tự chạy trước khi nghi code |
| `415` khi tạo song | Phải gửi multipart (`metadata`, `audio`, `cover?`), không gửi JSON |
| `400` `File content is not a valid audio file` (hoặc `image`) | File mẫu giả cũ bị từ chối vì byte đầu không hợp lệ; tạo lại `test-files` bằng script ở mục Test |
| `400` `File content does not match its extension` | Nội dung là loại khác với đuôi file (ví dụ nội dung WAV đặt tên `.mp3`) |
| `docker compose config` báo `... is required` | Thiếu `.env`, hoặc thiếu `DB_PASSWORD` hay `JWT_SECRET` |
| App trong Docker lặp lại `password authentication failed` | Đổi `DB_PASSWORD` sau khi volume `pgdata` đã tạo (Postgres chỉ nhận mật khẩu lúc khởi tạo). `docker compose down -v` (mất dữ liệu) hoặc đổi mật khẩu trong DB |
| `port is already allocated` | Còn tiến trình dùng cổng 8080, ví dụ `./mvnw spring-boot:run` |
| Container `unhealthy` | Xem `docker compose logs app`; kiểm tra `/actuator/health` |

## Giấy phép

[MIT](LICENSE).