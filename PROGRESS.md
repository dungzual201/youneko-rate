# Youneko Rate! — Progress

> Trạng thái chính thức của dự án. Cập nhật sau mỗi giai đoạn theo quy trình trong `SPEC.md`.

| Giai đoạn | Trạng thái | Commit hash | Ngày | Ghi chú |
|---|---|---|---|---|
| 1. Khởi tạo project, theme, navigation, Room schema đầy đủ, DI | DONE | `7675fba60de2bbfe73e2f3d84b746758f1629249` | 2026-08-17 | Android foundation đã build/test local PASS; CI run [#6](https://github.com/dungzual201/youneko-rate/actions/runs/32049411776) PASS với `assembleDebug` + `testDebugUnitTest`, artifact debug APK 19.6 MB. `compileSdk=37`, `targetSdk=36`. |
| 2. Rate & Review: nhập thủ công, chấm sao, tính trung bình, local search/sort/filter | DONE | `43d2cc2` | 2026-08-18 | Hotfix cascade đã đổi Album/Track/Artist từ REPLACE sang insert/update ABORT; Favorite đã gỡ hoàn toàn bằng migration Room 2→3; UI polish gồm palette mặc định, 5 sao half-fill, tooltip highlight/skip và mascot vector tạm. Regression migration test thêm; commit đã push cùng Phase 3. |
| 3. Import metadata từ tag file nhạc local | DONE | `0589742` + `60463e4` | 2026-08-18 | Đã hotfix lỗi SAF truyền extension `.audio` khiến jaudiotagger skip toàn bộ file: dùng display name, magic bytes, temp file đúng extension, cleanup finally và MediaMetadataRetriever fallback. Unit tests extension/magic và manual matrix FLAC/MP3 v2.3/v2.4/M4A-ALAC/WAV/OGG/file không extension PASS; Opus được chuyển qua fallback Android. CI Phase 4 cũng xác nhận build/test/artifact. |
| 4. Network, throttle, cache, MusicBrainz search và release lookup | DONE | `aeeb80a` | 2026-08-18 | Retrofit + kotlinx-serialization + OkHttp/Hilt; User-Agent; token bucket capacity 5/refill 1 token/s; retry 503/429; cache TTL 30 ngày; offline switch; Paging 3 local-first/online search; read-only MB preview; MockWebServer fixtures, TTL, HTTP error và token bucket tests PASS. CI [Android Build #32097543485](https://github.com/dungzual201/youneko-rate/actions/runs/32097543485) PASS với assembleDebug, unit tests và upload artifact. |
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
| Tài liệu, repository private và CI/CD skeleton | DONE | `6109cbffc7b6afc8c6914a4ace6283724dc97b3b` | 2026-08-17 | Đã push lên `github.com/dungzual201/youneko-rate`; CI skeleton đã được sử dụng thành công để build Android foundation ở giai đoạn 1. |
