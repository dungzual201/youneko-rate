# Youneko Rate! — Progress

> Trạng thái chính thức của dự án. Cập nhật sau mỗi giai đoạn theo quy trình trong `SPEC.md`.

| Giai đoạn | Trạng thái | Commit hash | Ngày | Ghi chú |
|---|---|---|---|---|
| 1. Khởi tạo project, theme, navigation, Room schema đầy đủ, DI | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; giai đoạn 0 chỉ chuẩn bị tài liệu, repository và CI/CD. |
| 2. Rate & Review: nhập thủ công, chấm sao, tính trung bình, local search/sort/filter | TODO | Chưa có | 2026-08-17 | Ưu tiên hoàn thành sớm sau giai đoạn 1; chưa viết code app. |
| 3. Import metadata từ tag file nhạc local | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu. |
| 4. Network, throttle, cache, MusicBrainz search và release lookup | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; phải tuân thủ token bucket 1 request/giây và User-Agent bắt buộc. |
| 5. Preview, chọn release, import Room, Cover Art Archive và dedupe | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu. |
| 6. Credits MusicBrainz, bảng Credit và tra cứu theo người | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; credits chỉ tải lazy khi người dùng yêu cầu. |
| 7. Provider phụ và quản lý nguồn/token/cache | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; Discogs, Last.fm, Deezer, ListenBrainz mặc định tắt theo SPEC. |
| 8. Audio Quality Checker phần 1: decode, FFT, spectrogram, technical info | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; không dùng ffmpeg-kit và không fake dữ liệu. |
| 9. Audio Quality Checker phần 2: heuristics, verdict, lưu, so sánh, badge | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; mọi chỉ số phải xuất phát từ PCM decode thật. |
| 10. Export/import backup, CSV, Markdown, auto backup và Python tool | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu. |
| 11. Stats, share ảnh, onboarding, đa ngôn ngữ, polish, unit test, README và APK | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; màn Open source licenses nằm ở giai đoạn này. |
| 12. PoC và decode audio tầng 2 mở rộng bằng thư viện prebuilt/JNI đã xác minh | TODO | Chưa có | 2026-08-17 | Tách khỏi giai đoạn 8; phải đo APK tăng thêm và test ít nhất một thiết bị arm64 trước khi chốt. |

## Quy ước trạng thái

- `TODO`: chưa bắt đầu.
- `IN-PROGRESS`: đang triển khai, chưa đủ điều kiện build/demo để kết thúc.
- `DONE`: đã build, demo, cập nhật tài liệu, commit và push thành công.

## Giai đoạn 0

Giai đoạn 0 là bước chuẩn bị ngoài 12 giai đoạn phát triển trong SPEC: đọc yêu cầu, dựng tài liệu dài hạn, khởi tạo repo private, thiết lập CI/CD và nêu rủi ro/câu hỏi. Chưa có mã nguồn ứng dụng Android nào được viết ở giai đoạn này.

| Hạng mục | Trạng thái | Commit hash | Ngày | Ghi chú |
|---|---|---|---|---|
| Tài liệu, repository private và CI/CD skeleton | DONE | `6109cbffc7b6afc8c6914a4ace6283724dc97b3b` | 2026-08-17 | Đã push lên `github.com/dungzual201/youneko-rate`; CI đã được kiểm chứng và hiện chờ Android project/Gradle wrapper của giai đoạn 1. |
