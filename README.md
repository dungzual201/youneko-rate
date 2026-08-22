# Youneko Rate!

> Ứng dụng Android offline-first để **chấm điểm, review album và track, quản lý credits, đọc lyrics local và phân tích chất lượng audio**.
>
> Đây là dự án beta. Youneko Rate! **không phải trình phát nhạc và sẽ không có chức năng phát nhạc**.

[![Android Build](https://github.com/dungzual201/youneko-rate/actions/workflows/android-build.yml/badge.svg)](https://github.com/dungzual201/youneko-rate/actions/workflows/android-build.yml)
[![Repository](https://img.shields.io/badge/repository-private-lightgrey)](https://github.com/dungzual201/youneko-rate)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Youneko Rate! là gì?

Youneko Rate! dành cho người nghe nhạc theo album và muốn lưu lại cảm nhận một cách có hệ thống. Ứng dụng đọc thư viện audio local, cho phép chấm điểm từng bài, tính điểm album, viết review, gắn thẻ, ghi listening log, tra credits và xem lyrics có sẵn trong file hoặc sidecar. Audio được giải mã **chỉ để phân tích**, không được gửi ra thiết bị âm thanh.

### Vì sao không có chức năng phát nhạc?

Đây là quyết định thiết kế cố ý: ứng dụng tập trung vào đánh giá, phân tích và bảo toàn dữ liệu người dùng, không cạnh tranh với trình phát nhạc chuyên dụng và không phải xử lý MediaSession, audio focus hay điều khiển notification. Regression guard trong repository kiểm tra rằng source không chứa `MediaPlayer`, `ExoPlayer`, `androidx.media3`, `MediaSession`, `AudioTrack` hoặc `previewUrl`.

> `MediaExtractor`, `MediaCodec` và `MediaMetadataRetriever` chỉ phục vụ đọc metadata, artwork và decode dữ liệu cho phân tích. Không có luồng playback.

## Tính năng hiện có trong code

| Khu vực | Hành vi đã triển khai |
|---|---|
| Quét thư viện | MediaStore nhiều volume, `READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE`, SAF tree, incremental generation/`DATE_MODIFIED`, ContentObserver debounce và PeriodicWorkManager. Scan có hai pha sẵn có: metadata rồi artwork/enrichment. Banner tiến trình cấp app chiếm trọn hàng header, hiển thị pha, số đếm và nút huỷ; khi xong header trở lại. |
| Metadata và artwork | Đọc tag local; artwork ưu tiên embedded picture, ảnh cùng thư mục và MediaStore albumart; cache tại `filesDir/covers/{albumId}.jpg`. Scan không gọi mạng để lấy cover. |
| Rate và review | Thang 5 sao/10 điểm/100 điểm với canonical storage 5 sao, điểm album simple/weighted/manual, review autosave, tags và listening log. |
| Lyrics | Đọc ID3 `USLT`/`SYLT`, Vorbis `LYRICS`, atom `©lyr`, sidecar `.lrc`/`.ttml`; TTML dùng `XmlPullParser`, `WordTiming`, agent và offset-time. Ứng dụng không crawl lyrics web. |
| Credits | File tags, MusicBrainz, Discogs, Genius, Deezer và iTunes metadata/provider theo code. Có source picker, chế độ riêng/gộp, link/MBID thủ công và nhập credits hàng loạt. Credit thủ công không bị fetch tự động ghi đè. |
| Audio analysis | Decode-only bằng `MediaExtractor`/`MediaCodec`, FFT 4096 điểm với cửa sổ Hann, thông tin codec/bitrate/sample rate/bit depth/channel/cutoff/slope/clipping/true peak/dynamic range/crest factor khi dữ liệu có sẵn. Verdict lossless/lossy là heuristic. |
| Sao lưu và khôi phục | Định dạng `.younekorate` là ZIP có manifest, database checkpoint WAL, covers, settings an toàn và CSV/JSON UTF-8 BOM; hỗ trợ import preview, replace có rollback, merge stable-key, remap cover và auto-backup SAF giữ 5 bản. ZIP không chứa file nhạc hoặc API token. |
| Stats/collection | Code hiện có module thống kê, chia sẻ ảnh qua FileProvider, collection, tìm kiếm nâng cao và trang nghệ sĩ; mức hoàn thiện chính thức phải đối chiếu `docs/PROGRESS.md`. |

Bốn tab chính là **Library**, **Rate**, **Analyze** và **Stats**. Library và Rate phục vụ quản lý/chấm điểm; Analyze chỉ giải mã để đo chất lượng; Stats tổng hợp dữ liệu đã lưu.

## Bảo toàn dữ liệu người dùng

Dữ liệu review, điểm, tag, listening log, credits thủ công và metadata local được lưu trong Room trên thiết bị. Ứng dụng không yêu cầu tài khoản, không có đồng bộ cloud trong code hiện tại và không sửa file nhạc của người dùng. File bị mất chỉ được đánh dấu `isMissing`; scanner không xoá rating, review hoặc credit thủ công.

Lyrics chỉ được đọc từ tag/sidecar local; ứng dụng không crawl lyrics web. Repository không dùng `fallbackToDestructiveMigration()` và không dùng `MANAGE_EXTERNAL_STORAGE`.

## Công nghệ và kiến trúc thực tế

| Thành phần | Giá trị thực tế trong repository |
|---|---|
| Ngôn ngữ/UI | Kotlin `2.4.10`, Jetpack Compose Material 3, Compose BOM `2026.08.00` |
| Android build | Android Gradle Plugin `9.3.1`, Gradle wrapper `9.5.0`, compile SDK `37`, min SDK `26`, target SDK `36` |
| Java | JDK `17`; `sourceCompatibility`, `targetCompatibility` và Kotlin JVM target đều là 17 |
| Local data | Room `2.8.4` với KSP, exported schemas và migration tới database version 16; DataStore Preferences `1.2.1` |
| DI/background | Hilt `2.60.1` (không dùng Koin), WorkManager `2.11.2` |
| HTTP/JSON | Retrofit `2.11.0`, OkHttp `4.12.0`, `kotlinx.serialization` `1.11.0`; không dùng Gson/Moshi làm JSON library chính |
| Media/tag/analysis | jaudiotagger `3.0.1`, MediaExtractor, MediaCodec, MediaMetadataRetriever, JTransforms `3.1`, Coil `2.7.0`, XmlPullParser |

Kiến trúc hiện tại là **MVVM theo hướng repository/data layer** trên Compose. Composable quan sát `StateFlow` từ ViewModel; ViewModel điều phối repository/use case và worker; Room DAO cung cấp `Flow`; DataStore giữ settings/checkpoint; Hilt cung cấp dependency; WorkManager xử lý scan, backup/restore và phân tích nền. Đây không phải một Clean Architecture tách package tuyệt đối.

## Build từ source

### Yêu cầu

Repository pin **JDK 17**, Gradle wrapper **9.5.0**, AGP **9.3.1**, compile SDK **37**, min SDK **26** và target SDK **36**. Android Studio không được pin bằng một file version trong repository; hãy dùng bản stable tương thích với AGP `9.3.1` và JDK `17`. Máy build cần có Android SDK platform 37 hoặc đặt `ANDROID_HOME`/`ANDROID_SDK_ROOT` trỏ tới SDK hợp lệ.

### Clone và assemble debug APK

```bash
git clone https://github.com/dungzual201/youneko-rate.git
cd youneko-rate
cp local.properties.example local.properties
# Nếu cần, điền đường dẫn SDK thật, ví dụ:
# sdk.dir=/absolute/path/to/android-sdk
./gradlew assembleDebug
```

APK được tạo tại:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `gradle/libs.versions.toml` và `app/schemas/` được commit để clone có thể build bằng wrapper và chạy migration/schema tests. File `local.properties` chỉ là cấu hình máy local và bị ignore.

### Token provider tuỳ chọn

Audit source và Git history không phát hiện token hardcode. Vì vậy code hiện tại **không cần BuildConfig credential fields** và không tự đọc Discogs/Genius/Last.fm token từ `local.properties`. Token được nhập trong app tại **Cài đặt → Nguồn credits**, mã hoá cục bộ bằng Android Keystore và không được đưa vào backup. Không nhập token thì provider tương ứng bị tắt hoặc báo cần token; app vẫn chạy với dữ liệu local và nguồn không yêu cầu token.

`local.properties.example` chỉ để trống `sdk.dir` nhằm tránh đưa đường dẫn SDK cá nhân vào repository. Không đặt secret thật vào file này.

## Quyền Android

| Quyền | Mục đích |
|---|---|
| `READ_MEDIA_AUDIO` trên Android 13+ | Đọc audio trong MediaStore |
| `READ_EXTERNAL_STORAGE` trên Android 12 trở xuống | Đọc audio trên Android cũ |
| `FOREGROUND_SERVICE` và `FOREGROUND_SERVICE_DATA_SYNC` | Chạy scan/sync nền có notification |
| `INTERNET` | Provider credits và cover/metadata online khi người dùng yêu cầu |

Ứng dụng không dùng `MANAGE_EXTERNAL_STORAGE`.

## Tình trạng phát triển

`docs/PROGRESS.md` là nguồn trạng thái chính thức. Một module có code không tự động có nghĩa là phase đã nghiệm thu hoàn tất; còn phải xét build, regression tests, tài liệu và kiểm thử thiết bị thật. Tại thời điểm setup repository, backup/export/import đã có bốn commit riêng (`f535995`, `85c3f6f`, `b19f792`, `48e5c11`), local verification và CI đều đã pass; một số phase cũ trong tài liệu tiến độ vẫn cần cập nhật trạng thái chính thức vì chúng được viết trước các commit mới.

Sandbox không có emulator/ADB, vì vậy không thể cài APK, chạy kiểm thử thiết bị thật hoặc tạo screenshot thiết bị. Repository không chứa screenshot giả.

## Tài liệu

Các specification, báo cáo, quyết định kiến trúc, nguồn dependency và progress report được đặt trong [`docs/`](docs/):

- [`docs/SPEC.md`](docs/SPEC.md) — đặc tả dự án.
- [`docs/PROGRESS.md`](docs/PROGRESS.md) — tiến độ chính thức.
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — các quyết định kiến trúc.
- Các `PHASE*.md`, `FIX_*.md`, `BUILD_SOURCES*.md` và `NETWORK_SOURCES*.md` — báo cáo và tài liệu theo giai đoạn.

## Release và license

Bản build phát hành được đăng tại [GitHub Releases](https://github.com/dungzual201/youneko-rate/releases). Repository dùng [MIT License](LICENSE).

## Nguồn dữ liệu online

Các nguồn online hiện có gồm [iTunes Search](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/), [MusicBrainz](https://musicbrainz.org) và [Cover Art Archive](https://coverartarchive.org) cho cover, cùng các provider credits theo code như [Discogs](https://www.discogs.com), [Genius](https://genius.com) và [Deezer](https://www.deezer.com). Các request có giới hạn tối đa 1 request/giây ở luồng tương ứng và User-Agent định danh hợp lệ. COV chỉ mở bằng trình duyệt ngoài để người dùng xem; ứng dụng không gọi API nội bộ của COV. Người dùng cần tôn trọng điều khoản sử dụng và giới hạn tốc độ của từng dịch vụ. Luồng scan MediaStore local và lyrics parser không gọi các dịch vụ này.

## Giấy phép

Copyright (c) 2026 dungzual201. Xem toàn văn tại [`LICENSE`](LICENSE).
