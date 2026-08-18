# Youneko Rate! — Progress

> Trạng thái chính thức của dự án. Cập nhật sau mỗi giai đoạn theo quy trình trong `SPEC.md`.

| Giai đoạn | Trạng thái | Commit hash | Ngày | Ghi chú |
|---|---|---|---|---|
| 1. Khởi tạo project, theme, navigation, Room schema đầy đủ, DI | DONE | `7675fba60de2bbfe73e2f3d84b746758f1629249` | 2026-08-17 | Android foundation đã build/test local PASS; CI run [#6](https://github.com/dungzual201/youneko-rate/actions/runs/32049411776) PASS với `assembleDebug` + `testDebugUnitTest`, artifact debug APK 19.6 MB. `compileSdk=37`, `targetSdk=36`. |
| 2. Rate & Review: nhập thủ công, chấm sao, tính trung bình, local search/sort/filter | DONE | `43d2cc2` | 2026-08-18 | Hotfix cascade đã đổi Album/Track/Artist từ REPLACE sang insert/update ABORT; Favorite đã gỡ hoàn toàn bằng migration Room 2→3; UI polish gồm palette mặc định, 5 sao half-fill, tooltip highlight/skip và mascot vector tạm. Regression migration test thêm; commit đã push cùng Phase 3. |
| 3. Import metadata từ tag file nhạc local | DONE | `0589742` + `60463e4` | 2026-08-18 | Đã hotfix lỗi SAF truyền extension `.audio` khiến jaudiotagger skip toàn bộ file: dùng display name, magic bytes, temp file đúng extension, cleanup finally và MediaMetadataRetriever fallback. Unit tests extension/magic và manual matrix FLAC/MP3 v2.3/v2.4/M4A-ALAC/WAV/OGG/file không extension PASS; Opus được chuyển qua fallback Android. CI Phase 4 cũng xác nhận build/test/artifact. |
| 4. Network, throttle, cache, MusicBrainz search và release lookup | DONE + HOTFIX | `aeeb80a` + `43366ad` + `9e8ec50` | 2026-08-18 | Online tách khỏi local FTS/EmptyLibrary, chỉ dùng query text; query escape/UTF-8 encode, DTO thật, không cache empty/error, Paging count đúng. Local full assemble/unit test/lint PASS; CI [Android Build #32128119253](https://github.com/dungzual201/youneko-rate/actions/runs/32128119253) PASS. |
| 5. Preview, chọn release, import Room, Cover Art Archive và dedupe | DONE + HOTFIX | `bb0f988` | 2026-08-18 | Cover Art Archive đã tách OkHttpClient/ImageLoader, follow redirect, fallback release-group → release, 404 NotFound, Coil cache và lưu local `filesDir/covers/{albumId}.jpg`; import progress là modal. CI [Android Build #32132402317](https://github.com/dungzual201/youneko-rate/actions/runs/32132402317) PASS. |
| 6. Credits MusicBrainz, bảng Credit và tra cứu theo người | DONE + HOTFIX | `9998b3f` + `ec22440` + `d642710` | 2026-08-18 | Đính chính parser theo fixture thật AMORTAGE: đọc release-level + recording-level + work-level relations, lưu begin/end, gộp semantic role/attribute và hiển thị album→track buckets. CI [#32139923234](https://github.com/dungzual201/youneko-rate/actions/runs/32139923234) PASS. |
| 7. Provider phụ và quản lý nguồn/token/cache | IN-PROGRESS | `27ca8d6` + `4f6ccfb` + `e810ca1` | 2026-08-18 | Discogs API/service đã triển khai: mặc định tắt, token DataStore, cache 30 ngày, client/rate limiter riêng 25 req/phút, merge credit/source chip và clear cache UI. Multi-source cover đã thêm iTunes/Deezer/CAA, cover picker grid, metadata source/width và migration Room 8→9; Last.fm/ListenBrainz service còn TODO. Full local verification PASS; CI sau push đang chờ. |
| 8. Audio Quality Checker phần 1: decode, FFT, spectrogram, technical info | IN-PROGRESS | `bb428b5` + `8623c99` + `7761f2a` | 2026-08-18 | Đã triển khai MediaExtractor/MediaCodec decode-only, FFT 4096 Hann/hop 2048; đã sửa bin→Hz, cutoff/slope/verdict/confidence data-driven và spectrum axes/cutoff marker. Cần test matrix thiết bị thật; chưa có spectrogram PNG export đầy đủ. Full local assemble/unit/lint PASS; CI sau push đang chờ. |
| 9. Audio Quality Checker phần 2: heuristics, verdict, lưu, so sánh, badge | IN-PROGRESS | Chưa bắt đầu | 2026-08-18 | Phase 9 chưa được bắt đầu theo chỉ thị. Chỉ được bắt đầu sau khi người dùng gửi ảnh xác nhận cài APK thực tế cho FLAC/MP3, spectrum, credits và cover picker. |
| 10. Export/import backup, CSV, Markdown, auto backup và Python tool | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu. |
| 11. Stats, share ảnh, onboarding, đa ngôn ngữ, polish, unit test, README và APK | TODO | Chưa có | 2026-08-17 | Chưa bắt đầu; màn Open source licenses nằm ở giai đoạn này. |
| 12. PoC và decode audio tầng 2 mở rộng bằng thư viện prebuilt/JNI đã xác minh | TODO | Chưa có | 2026-08-17 | Tách khỏi giai đoạn 8; phải đo APK tăng thêm và test ít nhất một thiết bị arm64 trước khi chốt. |

## Ghi chú kiểm thử session hiện tại

Sandbox không có emulator hoặc `adb` (`adb-not-installed`), nên không thể thực hiện logcat thực tế, kiểm thử tay, cài APK, xác minh dialog không bị bottom navigation che, decoder MediaCodec trên codec matrix, hoặc chụp ảnh. Không đoán thay cho bằng chứng. Sau A-D, local `assembleDebug`, `testDebugUnitTest` và `lintDebug` đều PASS ngày 2026-08-18; Room schema 9 đã sinh; `AudioAnalysisTest` và `Phase6CreditsTest` PASS. CI cho SHA mới sẽ được push sau khi commit tài liệu. Chưa có ảnh chụp thật và chưa có xác nhận cài APK; chưa báo hoàn thành sản phẩm và chưa bắt đầu Phase 9.

## Quy ước trạng thái

- `TODO`: chưa bắt đầu.
- `IN-PROGRESS`: đang triển khai, chưa đủ điều kiện build/demo để kết thúc.
- `DONE`: đã build, demo, cập nhật tài liệu, commit và push thành công.

## Giai đoạn 0

Giai đoạn 0 là bước chuẩn bị ngoài 12 giai đoạn phát triển trong SPEC: đọc yêu cầu, dựng tài liệu dài hạn, khởi tạo repo private, thiết lập CI/CD và nêu rủi ro/câu hỏi. Chưa có mã nguồn ứng dụng Android nào được viết ở giai đoạn này.

| Hạng mục | Trạng thái | Commit hash | Ngày | Ghi chú |
|---|---|---|---|---|
| Tài liệu, repository private và CI/CD skeleton | DONE | `6109cbffc7b6afc8c6914a4ace6283724dc97b3b` | 2026-08-17 | Đã push lên `github.com/dungzual201/youneko-rate`; CI skeleton đã được sử dụng thành công để build Android foundation ở giai đoạn 1. |


## Xác nhận A-D — 2026-08-18

Các sửa chữa A-D của `FIX_ANALYZE_COVERS_CREDITS.md` đã có bảy commit bắt buộc: `bb428b5`, `8623c99`, `7761f2a`, `a11425d`, `1cb0ba7`, `4f6ccfb`, `e810ca1`. Full local verification `assembleDebug`, `testDebugUnitTest` và `lintDebug` PASS; regression manual-credit test cũng PASS. GitHub Actions [Android Build #32147162916](https://github.com/dungzual201/youneko-rate/actions/runs/32147162916) trên SHA `3fd6f6277cdae980dff39119dc6ae8d30495d816` đã PASS toàn bộ build, unit tests, MainActivity launch regression và artifact upload.

APK debug local đã được tạo tại `app/build/outputs/apk/debug/youneko-rate-fft-credits-covers.apk`, kích thước khoảng 27 MB, SHA-256 `2fa75aa4b0b5a342b1be2f14abdd3b7d13234d373ec8a5311a228052055d1107`. Sandbox vẫn không có emulator/adb nên APK chưa được cài/chạy thủ công và chưa có ảnh xác nhận; Phase 9 chưa bắt đầu.


## Phase 13 update — 2026-08-18

Đã hoàn thiện ma trận audit trong `PHASE13_AUDIT.md`. Analyze foreground worker hiện phát progress theo file/index/tổng số/bước/frame, có notification action Huỷ và cancellation guard; FFT/verdict đã có reference band 1–4 kHz, ref−50 dB, stable 6 bins, slope 2 kHz và fallback bucket sau decode thành công. Credits đã có album/track union, Genius/Discogs settings và provider, merge roleGroup/source/diacritics, manual/file-tag preservation. Cover đã có validation title/artist + track/year, provenance `coverSource`/`coverWidth`/`coverUpdatedAt`, Room schema 10 và cover picker manual/provider metadata. Track bottom sheet đã chuyển đúng 5 mục và loại bỏ cắt text một dòng.

Regression `AudioAnalysisTest`, `CoverArtTest` và `Phase6CreditsTest` đã được mở rộng; focused AudioAnalysis và Phase6Credits hiện PASS. Cần chạy full verification sau toàn bộ thay đổi phase 13, commit theo chỉ thị, push CI và build APK. Sandbox không có emulator/adb nên không thể tự cài, chụp screenshot hay thay thế xác nhận thiết bị của người dùng; Phase 9 vẫn chưa bắt đầu.
