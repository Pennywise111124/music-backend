# Music Backend

Backend cho app Android Music Player: Spring Boot 4.1.1, PostgreSQL, JWT, Flyway.
Tài liệu thiết kế: `music-backend-design.md`. Bàn giao: `Session_Summary.md`.

## Yêu cầu

- JDK 21
- PostgreSQL (DB `musicapp`)
- Docker Desktop (chỉ để chạy test tự động)
- VS Code + extension REST Client (để chạy `requests.http`)

## Cài đặt lần đầu

1. Tạo database:
```sql
   CREATE DATABASE musicapp;
```
2. Đặt biến môi trường (PowerShell, mỗi phiên terminal):
```powershell
   $env:JWT_SECRET = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
   # tùy chọn: $env:DB_PASSWORD = "..."   $env:DB_URL = "jdbc:postgresql://localhost:5432/musicapp"
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

## Test

| Cách | Lệnh | Ghi chú |
|---|---|---|
| Tự động | `./mvnw test` | 77 test; Docker phải đang chạy; chạy riêng: `-Dtest=PlaylistTest` |
| Thủ công | mở `requests.http` | Chạy từ trên xuống, chạy TK1, TK2 trước. Token hết hạn sau 15 phút |

Thư mục `test-files/` cần có: `a.mp3`, `b.mp3`, `pixel.png`, `pixel2.png`, `notes.txt`, `evil.exe`, `empty.mp3` (0 byte), `big.mp3` (khoảng 22MB, nằm trong `.gitignore`). Nội dung file không được kiểm tra nên file giả cũng được:
```powershell
mkdir test-files
"fake audio" | Set-Content test-files\a.mp3
"fake audio 2" | Set-Content test-files\b.mp3
"fake image" | Set-Content test-files\pixel.png
"fake image 2" | Set-Content test-files\pixel2.png
"hello" | Set-Content test-files\notes.txt
"x" | Set-Content test-files\evil.exe
New-Item -ItemType File test-files\empty.mp3
$f = [IO.File]::Create("$PWD\test-files\big.mp3"); $f.SetLength(22MB); $f.Close()
```

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