# Youneko Rate! — Progress

> Trạng thái chính thức của dự án. Cập nhật sau mỗi giai đoạn theo quy trình trong `SPEC.md`.

| Giai đoạn | Trạng thái | Commit hash | Ngày | Ghi chú |
|---|---|---|---|---|
| 1. Khởi tạo project, theme, navigation, Room schema đầy đủ, DI | DONE | `7675fba60de2bbfe73e2f3d84b746758f1629249` | 2026-08-17 | Android foundation đã build/test local PASS; CI run [#6](https://github.com/dungzual201/youneko-rate/actions/runs/32049411776) PASS với `assembleDebug` + `testDebugUnitTest`, artifact debug APK 19.6 MB. `compileSdk=37`, `targetSdk=36`. |
| 2. Rate & Review: nhập thủ công, chấm sao, tính trung bình, local search/sort/filter | DONE | `43d2cc2` | 2026-08-18 | Hotfix cascade đã đổi Album/Track/Artist từ REPLACE sang insert/update ABORT; Favorite đã gỡ hoàn toàn bằng migration Room 2→3; UI polish gồm palette mặc định, 5 sao half-fill, tooltip highlight/skip và mascot vector tạm. Regression migration test thêm; commit đã push cùng Phase 3. |
| 3. Import metadata từ tag file nhạc local | DONE | `0589742` + `60463e4` | 2026-08-18 | Đã hotfix lỗi SAF truyền extension `.audio` khiến jaudiotagger skip toàn bộ file: dùng display name, magic bytes, temp file đúng extension, cleanup finally và MediaMetadataRetriever fallback. Unit tests extension/magic và manual matrix FLAC/MP3 v2.3/v2.4/M4A-ALAC/WAV/OGG/file không extension PASS; Opus được chuyển qua fallback Android. CI Phase 4 cũng xác nhận build/test/artifact. |
| 4. Network, throttle, cache, MusicBrainz search và release lookup | DONE + HOTFIX | `aeeb80a` + `43366ad` + `9e8ec50` | 2026-08-18 | Online tách khỏi local FTS/EmptyLibrary, chỉ dùng query text; query escape/UTF-8 encode, DTO thật, không cache empty/error, Paging count đúng. Local full assemble/unit test/lint PASS; CI [Android Build #32128119253](https://github.com/dungzual201/youneko-rate/actions/runs/32128119253) PASS. |
| 5. Preview, chọn release, import Room, Cover Art Archive và dedupe | DONE + HOTFIX | `bb0f988` | 2026-08-18 | Cover Art Archive đã tách OkHttpClient/ImageLoader, follow redirect, fallback release-group → release, 404 NotFound, Coil cache và lưu local `filesDir/covers/{albumId}.jpg`; import progress là modal. CI [Android Build #32132402317](https://github.com/dungzual201/youneko-rate/actions/runs/32132402317) PASS. |
| 6. Credits MusicBrainz, bảng Credit và tra cứu theo người | DONE + HOTFIX | `8cf4632` | 2026-08-18 | Credits đã gộp người/vai trò/nhạc cụ, có số đếm nhóm, tối đa 3 nhóm mở và footer nguồn MusicBrainz. CI [Android Build #32132402317](https://github.com/dungzual201/youneko-rate/actions/runs/32132402317) PASS. |
| 7. Provider phụ và quản lý nguồn/token/cache | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; Discogs, Last.fm, Deezer, ListenBrainz mặc định tắt theo SPEC. |
| 8. Audio Quality Checker phần 1: decode, FFT, spectrogram, technical info | BLOCKED | Chưa bắt đầu | 2026-08-18 | Chỉ bắt đầu sau khi A–C có CI xanh và người dùng gửi ảnh chụp thực tế chứng minh cover art, loading, Credits/tracklist và tab Analyze placeholder. |
| 9. Audio Quality Checker phần 2: heuristics, verdict, lưu, so sánh, badge | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; mọi chỉ số phải xuất phát từ PCM decode thật. |
| 10. Export/import backup, CSV, Markdown, auto backup và Python tool | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu. |
| 11. Stats, share ảnh, onboarding, đa ngôn ngữ, polish, unit test, README và APK | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; màn Open source licenses nằm ở giai đoạn này. |
| 12. PoC và decode audio tầng 2 mở rộng bằng thư viện prebuilt/JNI đã xác minh | TODO | Chưa có | 2026-08-17 | Tách khỏi giai đoạn 8; phải đo APK tăng thêm và test ít nhất một thiết bị arm64 trước khi chốt. |

## Ghi chú kiểm thử session hiện tại

Sandbox không có emulator hoặc `adb` (`adb-not-installed`), nên không thể thực hiện logcat thực tế, kiểm thử tay, cài APK, xác minh dialog không bị bottom navigation che, hoặc chụp bốn ảnh bắt buộc. Không đoán thay cho bằng chứng. A–C hiện đã compile sạch; full `assembleDebug`, `testDebugUnitTest` và `lintDebug` PASS ở local. GitHub Actions run [#32132402317](https://github.com/dungzual201/youneko-rate/actions/runs/32132402317) trên SHA `8cf4632` cũng PASS assemble debug APK, unit tests, MainActivity Robolectric regression và upload artifact. Chưa có ảnh chụp giả và chưa báo hoàn thành sản phẩm. Cần build APK gửi người dùng, sau đó người dùng xác minh trên thiết bị thật trước khi đánh dấu hotfix hoàn tất và trước khi chuyển Phase 8.

## Quy ước trạng thái

- `TODO`: chưa bắt đầu.
- `IN-PROGRESS`: đang triển khai, chưa đủ điều kiện build/demo để kết thúc.
- `DONE`: đã build, demo, cập nhật tài liệu, commit và push thành công.

## Giai đoạn 0

Giai đoạn 0 là bước chuẩn bị ngoài 12 giai đoạn phát triển trong SPEC: đọc yêu cầu, dựng tài liệu dài hạn, khởi tạo repo private, thiết lập CI/CD và nêu rủi ro/câu hỏi. Chưa có mã nguồn ứng dụng Android nào được viết ở giai đoạn này.

| Hạng mục | Trạng thái | Commit hash | Ngày | Ghi chú |
|---|---|---|---|---|
| Tài liệu, repository private và CI/CD skeleton | DONE | `6109cbffc7b6afc8c6914a4ace6283724dc97b3b` | 2026-08-17 | Đã push lên `github.com/dungzual201/youneko-rate`; CI skeleton đã được sử dụng thành công để build Android foundation ở giai đoạn 1. |
