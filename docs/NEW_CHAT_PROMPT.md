# Mở cuộc trò chuyện mới

## Đính kèm

1. `Session_Summary.md`
2. `music-backend-design.md`
3. `docs/PROGRESS.md`, `docs/ROADMAP.md`
4. Zip (không dùng rar) thư mục `src`, kèm `pom.xml` và `application.yml`.
   **Không đưa vào zip:** `target/`, `uploads/`, `big.mp3`, bất kỳ file nào chứa `JWT_SECRET` hay mật khẩu thật.
5. Nếu làm phần Android: các file Kotlin liên quan (Repository interface, Entity, DAO, ViewModel, màn hình đang sửa)

## Tin nhắn mẫu (dán vào đầu cuộc trò chuyện)

```
Mình đang làm Project 2: chuyển app Android Music Player (Kotlin, Compose, MVVM, Room) sang backend Spring Boot + PostgreSQL + JWT. Backend ĐÃ HOÀN TẤT (11 module, 77 test tự động xanh, Flyway). Mình sẽ gửi từng file một Session_Summary.md, music-backend-design.md, docs/PROGRESS.md, docs/ROADMAP.md.

Hãy đọc kỹ các file và xác nhận ngắn gọn đã hiểu. Cách làm việc giữ nguyên như trong Session_Summary mục 2: trả lời tiếng Việt; viết sẵn code trong code block để mình copy; phần logic chính để TODO cho mình tự viết, rồi mình dán lại để bạn review (giải thích bản chất khi sai); chỉ kết luận khi có bằng chứng, không suy đoán về file chưa thấy.

Thông tin Android: DI đang dùng <Hilt/Koin/thủ công>; minSdk <...>; chạy thử trên <emulator/máy thật>; mình đã gửi AuthRepository, UserEntity/UserDao, ViewModel và màn đăng nhập.

Hôm nay mình muốn: <chọn một>
- Bắt đầu nối app Android (ROADMAP mục A, bước A1).
- Đóng gói Docker (ROADMAP mục B).
- Làm việc củng cố backend: <ghi rõ việc>.
- Sửa lỗi/thay đổi: <mô tả>.
```

## Lưu ý

- Claude không thấy code trên máy bạn nếu không được upload; mọi review dựa trên file bạn gửi.
- Nếu đã sửa code sau lần cuối cập nhật tài liệu, nói rõ chỗ nào đã đổi.
- Cập nhật `Session_Summary.md` (mục 4, 9) và `docs/PROGRESS.md` sau mỗi module hoặc mỗi đợt thay đổi lớn.