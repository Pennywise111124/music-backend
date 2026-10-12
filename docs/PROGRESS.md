# Nhật ký tiến độ và kết quả (Backend)

Tiến độ phần Android nằm ở repo `MusicPlayer-Android-Kotlin` (`docs/PROGRESS.md`).

## Tổng quan

| Hạng mục | Kết quả |
|---|---|
| Module backend | 11/11 hoàn tất |
| Test thủ công (`requests.http`) | 70 phần, 518 request, mỗi request có mã và kết quả kỳ vọng. Lần chạy lại toàn bộ gần nhất là sau đợt upload (trước khi `test-files` chuyển sang chữ ký hợp lệ); phần OP (3 request) thêm sau cùng |
| Test tự động | **93 test**, 0 lỗi (đối chiếu `target/surefire-reports`) |
| Migration | Flyway V1 + V2, chạy được trên DB trống (Testcontainers và Docker) |
| Docker | `docker compose up` dựng cả hệ thống từ máy trống; health `UP`; chạy bằng user thường |
| CI | GitHub Actions: test + build image, xanh trên commit `40824ce` |
| Android | Đã nối backend, đã bỏ Room (xem repo Android) |

## Dòng thời gian

| Giai đoạn | Nội dung | Kết quả |
|---|---|---|
| 0. Thiết kế | Đọc source Android, kiểm kê tính năng, thiết kế schema và API | `music-backend-design.md` bản đầu |
| 1. Auth | Register/login/refresh, JWT, BCrypt, ban | 9 test đầu đạt; sửa 4 bug |
| 2. Genre | CRUD, trùng tên không phân biệt hoa/thường | GN1-30 đạt; sửa lỗi 401 thay vì 403/400 do `/error` |
| 3. Artist | CRUD, tìm kiếm, Top; `popularity` tự tính | AR1-33 đạt |
| 4. Album | CRUD, `@ManyToOne`, EntityGraph | AL0a-AL44 đạt |
| 5. Song | CRUD, phân trang, ràng buộc artist-album-genre, 409 khi đang dùng | SG0a-SG67 đạt |
| 6. Favorite | Idempotent, phân trang, dọn khi xóa song | FV0a-FV35 đạt |
| 7. Follow | `ON CONFLICT`, popularity atomic, `followed` không N+1 | FL0a-FL33 đạt |
| 8. Recently Played | Upsert, `LIMIT` 30, job `@Scheduled` | RP0a-RP27 đạt |
| 9. Playlist | Quyền 404/403, reorder, khóa dòng, dọn dữ liệu | PL0a-PL77 đạt |
| 10. Admin User | Danh sách, ban, xóa dây chuyền, quy tắc admin | US0a-US43 đạt; US11 từng nghi lỗi, thực ra do thứ tự chạy test |
| 11. File upload | `StorageService`, multipart, dọn file theo transaction | UP0a-UP67 đạt |
| 12. Flyway | V1 từ pg_dump, V2 unique index, `validate` | Bật thành công trên DB cũ (baseline) và DB trống |
| 13. Test tự động | JUnit + Testcontainers | 77 test xanh |
| 14. Sửa lỗi sau rà soát | `DB_URL`/`DB_USER` thật sự đọc từ môi trường; cấm `@` trong username; filter tra user bằng `findByUsername` | 79 test xanh |
| 15. Docker và health | `Dockerfile`, compose, Actuator `/actuator/health` công khai | 81 test xanh; DK1-DK9 (bảng dưới) |
| 16. Thời gian UTC | `configureTimeZone()`, `-Duser.timezone=UTC` trong Docker | 84 test xanh (`TimeZoneTest`) |
| 17. Lỗi 403 JSON | `JsonAccessDeniedHandler` | 85 test xanh |
| 18. Chữ ký file | `FileSignature` + kiểm tra khớp đuôi | 93 test xanh |
| 19. CI và Git | GitHub Actions; đẩy lịch sử lên `main`; tag `v1.1-docker-ci` | CI xanh trên `40824ce` |

## Test thủ công theo nhóm (kết quả cuối)

| Nhóm | Phạm vi | Kết quả |
|---|---|---|
| A, TK, RF | Auth, token (kèm RF1b, RF7-RF10) | Đạt |
| GN | Genre 30 test | Đạt |
| AR | Artist 33 test | Đạt |
| AL | Album 44 test | Đạt |
| SG | Song 67 test (multipart) | Đạt |
| FV | Favorite 35 test | Đạt |
| FL | Follow 33 test | Đạt |
| RP | Recently Played 27 test | Đạt |
| PL | Playlist 77 test | Đạt |
| US | Admin User 43 test (+ US11b, US34b) | Đạt |
| UP | Upload 67 test | Đạt (UP19 `audio/mpeg`; UP24, UP26 cũ ra 400 do Tomcat) |
| OP | Health, endpoint actuator khác, 403 JSON | Đạt (OP1 200 `UP`, OP2 401, OP3 403 JSON có `message` là `Access denied`) |

Lưu ý: các test thủ công dùng file mẫu **có chữ ký hợp lệ** từ giai đoạn 18 (script trong README).

## Test tự động

| Class | Số test | Nội dung chính |
|---|---|---|
| AuthSecurityTest | 23 | Đăng ký/đăng nhập, không lộ mật khẩu, loại token, phân quyền, ban có hiệu lực ngay, admin không xử lý admin, xóa user dọn dây chuyền, username có `@` bị từ chối, token dùng được khi username trùng email người khác, health công khai và endpoint actuator khác được bảo vệ, 403 có body JSON |
| PlaylistTest | 13 | 404/403, thứ tự, reorder (4 kiểu sai), race thêm bài đồng thời, dọn khi xóa song |
| UploadTest | 17 | Header an toàn, Range, tên file client bị bỏ qua, rollback audio, đổi/xóa file, ảnh artist/album/playlist, xóa user dọn ảnh, nội dung phải là media thật, chấp nhận mọi chữ ký hợp lệ |
| FollowTest | 9 | Idempotent, popularity không âm kể cả khi dữ liệu lệch, race, dọn khi xóa artist |
| SongTest | 9 | Đọc, tìm, sắp xếp, phân trang, validate quan hệ, 409 kèm thông báo, album không đổi artist khi còn bài |
| FavoriteTest | 7 | Idempotent, race thêm trùng, cô lập theo user, phân trang |
| RecentlyPlayedTest | 6 | Upsert, giới hạn 30, job dọn, cô lập theo user |
| FileSignatureTest | 6 | Nhận dạng từng định dạng, sai nhóm, RIFF phân biệt WebP/WAV, đầu vào cụt/rỗng/rác, khung MPEG cần đủ ba bit đồng bộ |
| TimeZoneTest | 3 | `configureTimeZone` đặt UTC, mốc thời gian đường JPA và đường native đều là UTC |

## Kiểm chứng bằng phá code cố ý (kết quả)

| Phá thử | Test fail |
|---|---|
| Bỏ `&& userDetails.isEnabled()` trong `JwtAuthFilter` | `ban_takesEffectImmediately_andCanBeLifted` |
| Đổi `TYPE_ACCESS` thành `TYPE_REFRESH` ở filter | hầu hết test |
| `findByIdForUpdate` thành `findById` | `concurrentAddSong...` (`[0,1,1,1,1,1]`) |
| Đảo bước 1 và 2 khi xóa user | `deleteUser_cleansEverythingAndReducesPopularity` (popularity còn 1) |
| Bỏ `if (inserted == 1)` ở follow | 2 test follow |
| Bỏ điều kiện `deleted == 1` ở unfollow | `unfollow_isIdempotent...` |
| Bỏ `popularity > 0` | `unfollow_neverMakesPopularityNegative...` (ra -1); lần đầu **không** bị bắt, phải thêm test cố ý làm lệch dữ liệu |
| Bỏ dọn follow khi xóa artist | `deleteArtist_withFollowers...` (409) |
| `maxHistory` thành 1000 | `history_limitedTo30...` |
| Bỏ `deleteBySongId` của recently played | 2 test (409) |
| Bỏ kiểm tra album thuộc artist | `update_validatesRelations...` |
| Thêm lại `setAudioUrl` từ request | `update_neverTouchesFileUrls` |
| Bỏ `existsByGenreId` | `genreAlbumArtistInUse...`; lần đầu **không** bị bắt vì FK của DB vẫn trả 409, phải kiểm tra thêm thông báo |
| Bỏ check đổi artist của album | `albumWithSongs_cannotChangeArtist...` |
| Đổi sort favorite thành `asc` | `add_isIdempotent_newestFirst...` |
| Bỏ `afterRollback` khi tạo song | `createSong_badCover_rollsBackAlreadySavedAudio` |
| Bỏ `afterCommit` khi đổi audio / xóa song / xóa playlist | các test file tương ứng |
| Tìm song sau khi lưu file | `replaceAudio_invalidOrUnknown...` (thừa 1 file) |
| Bỏ dọn ảnh playlist khi xóa user | `deleteUser_removesTheirPlaylistImages` |
| Bỏ kiểm tra Content-Type | `createSong_invalidFiles_rejected...` |
| Bỏ header `nosniff` trong `FileController` | **vẫn xanh**: Spring Security tự thêm header này |
| Filter tra user bằng `findByUsernameOrEmail` (hoàn tác) | `token_worksEvenWhenUsernameEqualsAnotherUsersEmail` (201 mong đợi, nhận 401) |
| Bỏ `permitAll` của `/actuator/health/**` | `actuatorHealth_isPublic` (401) |
| Mở `permitAll` cho cả `/actuator/**` | `otherActuatorEndpoints_stayProtected` (404 thay vì 401) |
| Đổi `"UTC"` thành `"Asia/Ho_Chi_Minh"` trong `configureTimeZone` | cả 3 `TimeZoneTest`, đường JPA và đường native cùng lệch đúng 25200 giây |
| Bỏ `.accessDeniedHandler(...)` | `forbidden_hasJsonBody` (`PathNotFound $['message']`) |
| Bỏ bước kiểm tra chữ ký trong `store` | `upload_contentMustBeRealMedia` (400 mong đợi, nhận 200) |
| Bỏ bước so với đuôi khai báo | `upload_contentMustBeRealMedia` (ca WAV đặt tên `.mp3`) |
| `FileSignature.detect` luôn trả `mp3` | cả 6 test của `FileSignatureTest` |

## Docker: kiểm chứng

| # | Kiểm tra | Kết quả |
|---|---|---|
| DK1 | Thiếu `.env`, `docker compose config` | Báo `... is required` cho `DB_PASSWORD` và `JWT_SECRET` |
| DK2 | `up -d --build` từ máy chưa có image | `app` và `db` `healthy` |
| DK3 | Schema | 11 bảng, `flyway_schema_history` ghi V1, V2 thành công |
| DK4 | `/actuator/health`, `/actuator/env` | `UP` / 401 |
| DK5 | Tải file với `Range` | 206, `Content-Range` đúng |
| DK6 | `down` rồi `up` | Dữ liệu, file và token vẫn còn (`JWT_SECRET` cố định) |
| DK7 | `down -v` rồi `up` | Schema tạo lại, `users` có 0 dòng |
| DK8 | Người dùng tiến trình | `uid=100(app)` (không phải root), ghi được `/app/uploads` |
| DK9 | Profile và log | `prod` bật, không có câu SQL trong 500 dòng log gần nhất |

DK10 (khóa `JWT_SECRET` yếu làm app thoát) và DK11 (dữ liệu còn sau khi khởi động lại app) do người dùng báo bằng lời, chưa có log kèm theo.

## Số liệu đáng nhớ

- Sai sót tự phát hiện khi review: đếm sai số test nhiều lần, suy đoán sai về `JwtAuthFilter` (code đã đúng), khẳng định "ban hiệu lực ngay" trước khi test, mô tả sai kết quả test mất mạng. Bài học: không kết luận khi chưa có bằng chứng chạy thật.
- Hai ca test ban đầu **không** bắt được lỗi khi phá code (`popularity > 0`, `existsByGenreId`) đã được siết chặt.
- Khi rà soát code lúc bàn giao tìm ra hai lỗi thật mà tài liệu không nói: `DB_URL` bị ghi cứng, và nguy cơ trùng username với email.