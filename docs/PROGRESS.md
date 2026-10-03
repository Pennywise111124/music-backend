# Nhật ký tiến độ và kết quả

## Tổng quan

| Hạng mục | Kết quả |
|---|---|
| Module backend | 11/11 hoàn tất |
| Test thủ công (`requests.http`) | 69 phần, toàn bộ đạt, chạy lại hết sau đợt upload |
| Test tự động | 77 test, 0 lỗi |
| Migration | Flyway V1 + V2, chạy được trên DB trống (kiểm chứng bằng Testcontainers) |

## Dòng thời gian

| Giai đoạn | Nội dung | Kết quả |
|---|---|---|
| 0. Thiết kế | Đọc source Android, kiểm kê tính năng, thiết kế schema và API | `music-backend-design.md` bản đầu |
| 1. Auth | Register/login/refresh, JWT, BCrypt, ban | 9 test đầu đạt; sửa 4 bug (mục "Bài học" trong Session_Summary) |
| 2. Genre | CRUD, trùng tên không phân biệt hoa/thường | GN1-30 đạt; sửa lỗi 401 thay vì 403/400 do `/error` |
| 3. Artist | CRUD, tìm kiếm, Top; `popularity` tự tính (đổi so với thiết kế gốc) | AR1-33 đạt |
| 4. Album | CRUD, quan hệ `@ManyToOne`, EntityGraph | AL0a-AL44 đạt |
| 5. Song | CRUD, phân trang, ràng buộc artist-album-genre, 409 khi đang dùng | SG0a-SG67 đạt |
| 6. Favorite | Idempotent, phân trang, dọn khi xóa song | FV0a-FV35 đạt |
| 7. Follow | `ON CONFLICT`, popularity atomic, `followed` không N+1 | FL0a-FL33 đạt |
| 8. Recently Played | Upsert, `LIMIT` 30, job `@Scheduled` | RP0a-RP27 đạt; test tùy chọn với `max-history: 2` đạt |
| 9. Playlist | Quyền 404/403, reorder, khóa dòng, dọn dữ liệu | PL0a-PL77 đạt |
| 10. Admin User | Danh sách, ban, xóa dây chuyền, quy tắc admin | US0a-US43 đạt; US11 từng nghi lỗi, thực ra do thứ tự chạy test |
| 11. File upload | `StorageService`, multipart, dọn file theo transaction | UP0a-UP67 đạt; chuyển các request tạo bài hát sang multipart |
| 12. Flyway | V1 từ pg_dump, V2 unique index, `validate` | Bật thành công trên DB cũ (baseline) và DB trống |
| 13. Test tự động | JUnit + Testcontainers | 77 test xanh; kiểm chứng bằng phá code |

## Test thủ công theo nhóm (kết quả cuối)

| Nhóm | Phạm vi | Kết quả |
|---|---|---|
| A, TK, RF | Auth, token (kèm RF1b, RF7-RF10 sau khi thêm claim `type`) | Đạt |
| GN | Genre 30 test | Đạt |
| AR | Artist 33 test | Đạt |
| AL | Album 44 test | Đạt |
| SG | Song 67 test (tạo bằng multipart) | Đạt |
| FV | Favorite 35 test | Đạt |
| FL | Follow 33 test | Đạt |
| RP | Recently Played 27 test | Đạt |
| PL | Playlist 77 test | Đạt |
| US | Admin User 43 test (+ US11b, US34b) | Đạt |
| UP | Upload 67 test | Đạt (UP19 `audio/mpeg`; UP24, UP26 cũ ra 400 do Tomcat) |

## Test tự động

| Class | Số test | Nội dung chính |
|---|---|---|
| AuthSecurityTest | 18 | Đăng ký/đăng nhập, không lộ mật khẩu, loại token, phân quyền, ban có hiệu lực ngay, admin không xử lý admin, xóa user dọn dây chuyền |
| PlaylistTest | 13 | 404/403, thứ tự, reorder (4 kiểu sai), race thêm bài đồng thời, dọn khi xóa song |
| FollowTest | 9 | Idempotent, popularity không âm kể cả khi dữ liệu lệch, race cùng user và khác user, dọn khi xóa artist |
| RecentlyPlayedTest | 6 | Upsert, giới hạn 30, job dọn, cô lập theo user |
| FavoriteTest | 7 | Idempotent, race thêm trùng, cô lập theo user, phân trang |
| SongTest | 9 | Đọc, tìm, sắp xếp, phân trang, validate quan hệ, 409 kèm thông báo, album không đổi artist khi còn bài |
| UploadTest | 15 | Header an toàn, Range, tên file client bị bỏ qua, rollback audio, đổi/xóa file, ảnh artist/album/playlist, xóa user dọn ảnh |

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

## Số liệu đáng nhớ

- Sai sót tự phát hiện khi review: đếm sai số test nhiều lần, suy đoán sai về `JwtAuthFilter` (code đã đúng), khẳng định "ban hiệu lực ngay" trước khi test. Bài học: không kết luận khi chưa có bằng chứng chạy thật.
- Hai ca test ban đầu **không** bắt được lỗi khi phá code (`popularity > 0`, `existsByGenreId`) đã được siết chặt.