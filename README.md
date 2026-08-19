# Youneko Rate!

> Ứng dụng Android để **chấm điểm, đánh giá và phân tích** thư viện nhạc của bạn.
> Đây là một dự án beta, không phải trình phát nhạc — và sẽ không trở thành trình phát nhạc.

[![Android Build](https://github.com/dungzual201/youneko-rate/actions/workflows/android-build.yml/badge.svg)](https://github.com/dungzual201/youneko-rate/actions/workflows/android-build.yml)
[![Repository](https://img.shields.io/badge/repository-private-lightgrey)](https://github.com/dungzual201/youneko-rate)

---

## Youneko Rate! là gì?

Youneko Rate! dành cho người **nghe nhạc theo album** và muốn lưu lại cảm nhận một cách có hệ thống: chấm điểm từng bài, tính điểm album, viết review, gắn thẻ, ghi nhật ký nghe, tra credits, xem lời bài hát có trong file và kiểm tra chất lượng thực tế của file nhạc bằng phân tích phổ tần.

Ứng dụng đọc thư viện nhạc trong máy nhưng **không phát nhạc**. Bạn nghe bằng ứng dụng nào cũng được; Youneko Rate! là nơi ghi lại những gì bạn đã nghe và những gì bạn nhận xét.

### Vì sao không có chức năng phát nhạc?

Đây là quyết định thiết kế cố ý, không phải tính năng còn thiếu:

- Giữ ứng dụng nhẹ và không cạnh tranh với trình phát nhạc chuyên dụng.
- Tránh toàn bộ phức tạp của MediaSession, notification điều khiển và audio focus.
- Tập trung vào việc **đánh giá**, **phân tích** và **bảo toàn dữ liệu người dùng**.

Ràng buộc này được kiểm tra bằng regression guard trong repository. Mã nguồn không được chứa `MediaPlayer`, `ExoPlayer`, `androidx.media3`, `MediaSession`, `AudioTrack` hoặc dùng `previewUrl` từ API. `MediaExtractor`, `MediaCodec` và `MediaMetadataRetriever` chỉ được dùng để đọc metadata, artwork hoặc giải mã phục vụ phân tích; không có luồng nào xuất PCM ra thiết bị âm thanh.

---

## Tính năng hiện có trong code

### Thư viện và quét file

- Đọc audio từ MediaStore qua `READ_MEDIA_AUDIO` trên Android 13 trở lên và `READ_EXTERNAL_STORAGE` trên Android 12 trở xuống.
- Chọn thêm thư mục bằng Storage Access Framework (SAF) và lưu quyền truy cập lâu dài.
- Quét nhiều external volume, lọc audio có `IS_MUSIC` hoặc `IS_PODCAST`, hỗ trợ quét tăng dần theo generation và `DATE_MODIFIED`.
- ContentObserver debounce 2 giây và PeriodicWorkManager 15 phút với ràng buộc battery-not-low, foreground progress notification và foreground service type `dataSync`.
- Scan MediaStore được tách thành hai pha: pha một đọc cursor và ghi batch để album/track xuất hiện sớm; pha hai đọc tag, lyrics và artwork nền với giới hạn song song 4. Hash 64 KiB chỉ được tính khi cần khớp lại track.
- Album dùng fallback nghệ sĩ theo thứ tự `albumArtist → artist → Không rõ nghệ sĩ`; track không có album được đánh dấu `isStandalone`.
- Artwork khi scan không gọi mạng: ưu tiên artwork nhúng, ảnh `cover.jpg`/`folder.jpg` cùng thư mục và MediaStore albumart. Artwork được cache tại `filesDir/covers/{albumId}.jpg`, JPEG quality 92, cạnh dài tối đa 1000 px.
- File bị xoá hoặc di chuyển không làm mất điểm, review hay credit thủ công. Track được đánh dấu `isMissing` và có thể được khớp lại bằng MediaStore ID, đường dẫn/tên file hoặc stable key khi thật sự cần.

Các nguồn cover online thuộc luồng provider/import riêng, không phải luồng scan local. Đây là điểm đã hiệu chỉnh so với bản nháp ban đầu.

### Đánh giá

- Chấm điểm từng bài theo thang cấu hình 5 sao, 10 điểm hoặc 100 điểm, với canonical storage 5 sao.
- Tính điểm album theo trung bình đơn giản hoặc trọng số thời lượng; hỗ trợ điểm album thủ công.
- Review album và track có autosave, lưu revision gần nhất và không bị scan xoá.
- Thẻ tuỳ chỉnh, highlight/skip và nhật ký nghe kèm ghi chú.

Review hiện được lưu dưới dạng văn bản trong Room; README không gọi đây là Markdown renderer vì code hiện tại không có cam kết đó.

### Lời bài hát

- Đọc lyrics nhúng trong file: ID3 `USLT`/`SYLT`, Vorbis comment `LYRICS`, atom `©lyr` và sidecar `.lrc`/`.ttml` cùng tên.
- Parser TTML dùng `XmlPullParser`, tắt xử lý DOCDECL, giữ khoảng trắng giữa span, tách timing từng từ thành `WordTiming`, lưu `agent` ở field riêng và hỗ trợ `x-bg`, `x-translation` cùng offset-time.
- Khi TTML lỗi, parser fallback về plain text và ghi log chẩn đoán; có chốt an toàn để marker thời gian hoặc prefix agent không lọt vào text.
- Lyrics có section thu gọn, xem toàn màn hình bằng `LazyColumn`, copy chỉ chữ thuần và tuỳ chọn hiển thị mốc thời gian dòng.
- Ứng dụng **không crawl lyrics từ internet**.

### Credits

- Có nguồn file tags, MusicBrainz, Discogs, Genius, Deezer và iTunes metadata/provider theo code hiện tại.
- Nguồn credits được bật/tắt bằng chip; có chế độ xem riêng từng nguồn và chế độ gộp.
- Trạng thái nguồn được biểu diễn rõ ràng, gồm có dữ liệu, trống, thiếu token, không khớp, giới hạn tốc độ hoặc lỗi mạng.
- Hỗ trợ link/MBID thủ công và nhập credit thủ công, kể cả dán hàng loạt. Credit thủ công được bảo toàn, không bị fetch tự động ghi đè.
- Credits tồn tại ở cả mức album và mức track; parser đọc quan hệ release, recording và work theo fixture đã kiểm chứng.

### Phân tích chất lượng audio

- Giải mã decode-only bằng `MediaExtractor` và `MediaCodec`, không phát âm thanh.
- Phân tích FFT 4096 điểm với cửa sổ Hann và các đoạn mẫu giới hạn; hiển thị thông tin codec, bitrate, sample rate, bit depth, số kênh, cutoff, slope, clipping, true peak, dynamic range và crest factor khi dữ liệu có sẵn.
- Có tiến độ theo file/bước, foreground notification và huỷ theo yêu cầu người dùng.
- Verdict lossless/lossy là heuristic dựa trên dữ liệu phân tích; cần kiểm tra thêm trên thiết bị và ma trận codec thực tế trước khi coi là hoàn thiện.

### Thống kê, xuất dữ liệu và các module beta

Code hiện có các module cho StatsScreen, share image qua FileProvider, xuất CSV/JSON, backup/restore `.younekorate`, collection và artist page. Tuy nhiên các module này chưa được đánh dấu hoàn tất trong bảng tiến độ chính thức vì vẫn cần audit UI, kiểm thử thiết bị và hoàn thiện tài liệu. README không coi chúng là release-ready.

---

## Tình trạng phát triển

Bảng dưới đây giữ **PROGRESS.md làm nguồn trạng thái chính thức**. Việc một module đã tồn tại trong code không tự động biến phase thành `DONE`; trạng thái `DONE` còn yêu cầu đủ kiểm thử, tài liệu và điều kiện nghiệm thu theo quy trình dự án.

| Giai đoạn | Nội dung | Trạng thái chính thức |
|:---:|---|:---:|
| 1 | Khởi tạo project, theme, navigation, Room schema đầy đủ, DI | ✅ DONE |
| 2 | Rate & Review: nhập thủ công, chấm sao, tính trung bình, local search/sort/filter | ✅ DONE |
| 3 | Import metadata từ tag file nhạc local | ✅ DONE |
| 4 | Network, throttle, cache, MusicBrainz search và release lookup | ✅ DONE + HOTFIX |
| 5 | Preview, chọn release, import Room, Cover Art Archive và dedupe | ✅ DONE + HOTFIX |
| 6 | Credits MusicBrainz, bảng Credit và tra cứu theo người | ✅ DONE + HOTFIX |
| 7 | Provider phụ và quản lý nguồn/token/cache | 🚧 IN-PROGRESS |
| 8 | Audio Quality Checker phần 1: decode, FFT, spectrogram, technical info | 🚧 IN-PROGRESS |
| 9 | Audio Quality Checker phần 2: heuristics, verdict, lưu, so sánh, badge | 🚧 IN-PROGRESS / chưa được nghiệm thu hoàn tất |
| 10 | Export/import backup, CSV, Markdown, auto backup và Python tool | 📋 Chưa bắt đầu theo PROGRESS.md |
| 11 | Stats, share ảnh, onboarding, đa ngôn ngữ, polish, unit test, README và APK | 📋 Chưa bắt đầu theo PROGRESS.md |
| 12 | PoC và decode audio tầng 2 mở rộng bằng thư viện prebuilt/JNI | 📋 Chưa bắt đầu theo PROGRESS.md |
| Bổ sung sau bản PROGRESS hiện tại | MediaStore scan hai pha, embedded lyrics, TTML WordTiming và artwork local cache | 🚧 Đã có code và regression tests; cần cập nhật trạng thái chính thức trong PROGRESS.md |

### Audit chênh lệch với bản nháp

Bản nháp ghi phase 7–8.5 là hoàn tất và phase 9–12 ở trạng thái khác, nhưng `PROGRESS.md` chính thức vẫn ghi phase 7–9 là `IN-PROGRESS` và phase 10–12 là chưa bắt đầu. README này dùng trạng thái chính thức thay vì tự nâng trạng thái dựa trên tên package hoặc sự tồn tại của module. Các thay đổi scan/lyrics/artwork mới nhất cũng chưa được cập nhật thành một dòng phase chính thức trong `PROGRESS.md`, nên được ghi là **đã có code nhưng chưa nghiệm thu hoàn tất**.

---

## Ảnh chụp

Bỏ qua ảnh chụp trong bản beta hiện tại. Giao diện vẫn đang hoàn thiện và sandbox không có emulator/ADB để tạo screenshot thiết bị thật. Repository không chứa ảnh giả hoặc ảnh dashboard thay thế cho màn hình ứng dụng.

---

## Công nghệ và kiến trúc

### Nền tảng và thư viện chính

- **Kotlin 2.4.10** và **Jetpack Compose Material 3** theo Compose BOM `2026.08.00`.
- **Android Gradle Plugin 9.3.1**, compile SDK **37**, min SDK **26**, target SDK **36**.
- **Room 2.8.4** với KSP, schema export và chuỗi migration; database hiện tại đã có migration tới version 16.
- **WorkManager 2.11.2** cho scan và worker nền; **DataStore Preferences 1.2.1** cho settings/checkpoint.
- **Hilt 2.60.1** và `androidx.hilt:hilt-navigation-compose:1.4.0` cho dependency injection. Dự án dùng **Hilt, không dùng Koin**.
- **Retrofit 2.11.0** với **OkHttp 4.12.0** và logging interceptor cho HTTP. CoverArt, MusicBrainz và provider dùng client/rate-limit/cache theo luồng tương ứng.
- **kotlinx.serialization JSON 1.11.0** và Retrofit kotlinx-serialization converter `1.0.0`. Dự án không dùng Gson/Moshi làm JSON library chính.
- **Coil 2.7.0** cho hiển thị ảnh; **JTransforms 3.1** cho FFT; **jaudiotagger 3.0.1** và `DocumentFile 1.1.0` cho tag/SAF.
- **MediaExtractor / MediaCodec** cho decode phân tích, **MediaMetadataRetriever** cho metadata/artwork fallback và **XmlPullParser** cho TTML.

### Kiến trúc thực tế

Kiến trúc hiện tại là **MVVM theo hướng repository/data layer** trên Compose. Composable screen quan sát state từ ViewModel bằng `StateFlow`; ViewModel điều phối use case/repository và worker; Room DAO cung cấp dữ liệu local qua `Flow`; DataStore giữ settings/checkpoint; Hilt cung cấp dependency; WorkManager xử lý scan, import, export/backup và phân tích nền. Đây là MVVM + repository/data layer thực tế, không phải một Clean Architecture tách package tuyệt đối.

---

## Cài đặt

### Tải bản build sẵn

Repository hiện **chưa có GitHub Release**. Khi có bản phát hành, link chính thức sẽ là:

[GitHub Releases](https://github.com/dungzual201/youneko-rate/releases)

### Tự build

```bash
git clone https://github.com/dungzual201/youneko-rate.git
cd youneko-rate
./gradlew assembleDebug
```

APK nằm ở `app/build/outputs/apk/debug/`.

### Quyền cần cấp

| Quyền | Dùng để làm gì |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+) | Đọc file audio trong MediaStore |
| `READ_EXTERNAL_STORAGE` (Android ≤ 12) | Đọc file audio trên Android cũ |
| `FOREGROUND_SERVICE` | Cho worker foreground chạy với notification |
| `FOREGROUND_SERVICE_DATA_SYNC` | Khai báo loại foreground service cho scan/sync dữ liệu |
| `INTERNET` | Provider credits và luồng cover/metadata online khi người dùng yêu cầu |

Ứng dụng **không** dùng `MANAGE_EXTERNAL_STORAGE`.

### Token API tuỳ chọn

Discogs và Genius có thể cần token theo provider. Token được nhập trong **Cài đặt → Nguồn credits**, lưu cục bộ và không được đưa vào file backup. Không nhập token thì các provider đó có thể báo trạng thái cần token; app vẫn có nguồn tag/file và provider khác.

Apple/iTunes Search trong code được dùng cho metadata/cover lookup; không được mô tả như một nguồn credits hoàn chỉnh. Đây là điểm đã hiệu chỉnh so với bản nháp.

---

## Dữ liệu của bạn

- Dữ liệu review, điểm, tag, listening log, credits thủ công và metadata local được lưu trong Room trên thiết bị.
- Ứng dụng không yêu cầu tài khoản và không có đồng bộ cloud trong code hiện tại.
- Ứng dụng không sửa file nhạc của bạn; scanner và tag reader chỉ đọc file.
- Scan không được phép xoá rating, review hoặc credit thủ công; file mất chỉ chuyển trạng thái `isMissing`.
- Repository không dùng `fallbackToDestructiveMigration()`. Room migration được giữ để bảo vệ dữ liệu khi nâng schema.
- Lyrics chỉ đọc từ tag/sidecar local; không crawl lyrics web.

---

## Ghi nhận nguồn dữ liệu

Một số luồng online có thể dùng [MusicBrainz](https://musicbrainz.org), [Cover Art Archive](https://coverartarchive.org), [Discogs](https://www.discogs.com), [Genius](https://genius.com), [Deezer](https://www.deezer.com) và [iTunes Search](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/). Người dùng cần tôn trọng điều khoản và giới hạn tốc độ của từng dịch vụ. Luồng scan MediaStore local không gọi các dịch vụ này.

---

## Tài liệu nội bộ

- [`PROGRESS.md`](PROGRESS.md) — tiến độ chính thức từng phase.
- [`DECISIONS.md`](DECISIONS.md) — quyết định kiến trúc và lý do.
- [`FIX_*.md`](.) — đặc tả chi tiết từng lần sửa lỗi hoặc thêm tính năng.

---

## Giấy phép

Repository hiện **chưa khai báo license**: không có file `LICENSE`/`COPYING` ở gốc repo và GitHub repository metadata không có SPDX license. Vì vậy README không tự gán MIT, Apache-2.0 hay license khác. Nếu muốn cho phép bên thứ ba sử dụng hoặc phân phối code, cần thêm file license chính thức vào một commit riêng.

---

## Tài liệu tham khảo

1. [GitHub repository — dungzual201/youneko-rate](https://github.com/dungzual201/youneko-rate)
2. [Android Developers — MediaStore audio](https://developer.android.com/reference/android/provider/MediaStore.Audio.Media)
3. [Android Developers — Room](https://developer.android.com/training/data-storage/room)
4. [Android Developers — WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
5. [Android Developers — Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider)

---

<p align="center">Làm bằng sự cẩn trọng cho những người nghe nhạc theo album.</p>
