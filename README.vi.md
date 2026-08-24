<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/logo_paw.png">
    <img src="docs/images/logo_paw.png" width="140" alt="Logo chân mèo Youneko Rate">
  </picture>
  <h1>Youneko Rate!</h1>
  <p>Chấm điểm thư viện nhạc local riêng tư, rõ ràng và offline.</p>
  <p>
    <a href="README.md">Read this README in English</a>
  </p>
  <p>
    <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/platform-Android-3DDC84.svg" alt="Platform Android"></a>
    <a href="app/build.gradle.kts"><img src="https://img.shields.io/badge/minSdk-26-blue.svg" alt="minSdk 26"></a>
    <a href="gradle/libs.versions.toml"><img src="https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg" alt="Kotlin 2.4.10"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-yellow.svg" alt="License MIT"></a>
    <img src="https://img.shields.io/badge/100%25%20Offline-6750A4?style=flat&logoColor=white" alt="100% Offline">
  </p>
</div>

Youneko Rate! là một người bạn đồng hành **offline-first** dành cho những ai thích lưu lại cảm nhận về album một cách có hệ thống. App đọc thư viện audio local, lưu điểm và review vào Room, đồng thời cung cấp công cụ phân tích và sắp xếp dữ liệu mà không biến thành một trình phát nhạc khác.

## ✨ Tính năng

<img src="docs/images/cat_peek.png" width="90" align="right" alt="Mèo chibi ló lên">

Bốn tab chính trong code hiện tại là:

| Tab | App làm gì |
|---|---|
| **Library** | Quét MediaStore và thư mục SAF tùy chọn theo hai pha, kèm banner tiến trình. Pha đầu đọc metadata local, sau đó enrichment artwork; lúc scan không tự tải cover từ mạng. |
| **Rate** | Cho phép chấm điểm thủ công từng bài, review album/bài, quản lý tag và listening log, đồng thời hiển thị gradient album lấy từ dữ liệu Palette. |
| **Analyze** | Chỉ decode audio để phân tích. STFT/FFT báo codec, bitrate, sample rate, bit depth và các chỉ số phổ khi dữ liệu đủ, sau đó đưa ra verdict heuristic về chất lượng codec. |
| **Stats** | Tổng hợp rating và kết quả phân tích đã lưu, hiển thị phân phối/xếp hạng, rồi render ảnh chia sẻ 1080×1350 với preview, nút chia sẻ và lưu cục bộ. |

App còn có trình đọc credits và lyrics local, collections, tìm kiếm nâng cao và trang nghệ sĩ. Với ảnh bìa, **Find cover** dùng luồng hai bước: mở website COV bằng trình duyệt ngoài để người dùng tự tìm/tải ảnh, sau đó chọn ảnh từ thư viện Android. App không gọi API riêng của COV. Sao lưu/khôi phục dùng ZIP `.younekorate` gồm database, cover, settings và dữ liệu JSON/CSV. App có resource tiếng Việt và tiếng Anh, theme Material 3/Material You, icon chân mèo và splash screen.

## 📸 Screenshots

Repository vẫn đang ở giai đoạn beta nên không chứa screenshot thiết bị giả. Bốn ô dưới đây là placeholder có chủ đích để bổ sung ảnh chụp thật sau này.

| Library | Rate |
|---|---|
| <!-- ![Library](docs/images/screenshot_library.png) --> <!-- TODO: bổ sung screenshot thiết bị thật --> | <!-- ![Rate](docs/images/screenshot_rate.png) --> <!-- TODO: bổ sung screenshot thiết bị thật --> |

| Chi tiết album | Stats |
|---|---|
| <!-- ![Chi tiết album](docs/images/screenshot_album_detail.png) --> <!-- TODO: bổ sung screenshot thiết bị thật --> | <!-- ![Stats](docs/images/screenshot_stats.png) --> <!-- TODO: bổ sung screenshot thiết bị thật --> |

## 🛠️ Công nghệ và kiến trúc

Các phiên bản dưới đây được đọc trực tiếp từ `gradle/libs.versions.toml` và `app/build.gradle.kts`.

| Nhóm | Thành phần thực tế |
|---|---|
| Ngôn ngữ và UI | Kotlin `2.4.10`, Jetpack Compose, Compose BOM `2026.08.00`, Material 3 |
| Android build | Android Gradle Plugin `9.3.1`, Gradle wrapper `9.5.0`, compile SDK `37`, min SDK `26`, target SDK `36` |
| Dữ liệu local | Room `2.8.4` dùng KSP và exported schema; DataStore Preferences `1.2.1` |
| Dependency injection | Hilt `2.60.1` và Hilt Navigation Compose `1.4.0`, không dùng Koin |
| Tác vụ nền | WorkManager `2.11.2`; scan, phân tích và backup dài đều chạy qua worker khi phù hợp |
| HTTP và JSON | Retrofit `2.11.0`, OkHttp `4.12.0`, kotlinx.serialization JSON `1.11.0` |
| Ảnh và artwork | Coil `2.7.0`, AndroidX Palette `1.0.0`, AndroidX ExifInterface `1.3.7` |
| Audio và tag | MediaExtractor, MediaCodec, MediaMetadataRetriever, jaudiotagger `3.0.1`, JTransforms `3.1` |
| AndroidX chính | core-ktx `1.19.0`, core-splashscreen `1.0.1`, Activity Compose `1.13.0`, Navigation Compose `2.9.8` |

Kiến trúc hiện tại là MVVM thực dụng theo hướng repository/data layer. Composable quan sát `StateFlow` từ ViewModel; DAO cung cấp dữ liệu Room, Hilt cấp dependency, DataStore lưu preference/trạng thái scan, còn WorkManager xử lý các tác vụ dài. Đây không phải một triển khai Clean Architecture tách package tuyệt đối.

## 📱 Yêu cầu hệ thống

| Thiết lập | Giá trị |
|---|---:|
| `minSdk` | 26 |
| `targetSdk` | 36 |
| `compileSdk` | 37 |
| Java/JDK | 17 |
| Kotlin | 2.4.10 |

## 🚀 Build từ source

```bash
git clone https://github.com/dungzual201/youneko-rate.git
cd youneko-rate
cp local.properties.example local.properties
# Nếu Android Studio không tự tìm được SDK, đặt sdk.dir trong local.properties.
./gradlew assembleDebug
```

APK debug được tạo tại:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Có thể chạy toàn bộ kiểm tra local bằng:

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug compileDebugAndroidTestKotlin
```

## 🔐 Quyền Android

| Quyền | Lý do cần quyền |
|---|---|
| `READ_MEDIA_AUDIO` trên Android 13+ | Đọc metadata audio từ MediaStore. |
| `READ_EXTERNAL_STORAGE` trên Android 12 trở xuống | Đọc thư viện audio local trên Android cũ. |
| `INTERNET` | Truy cập provider metadata/credits online khi người dùng chủ động yêu cầu. |
| `FOREGROUND_SERVICE` và `FOREGROUND_SERVICE_DATA_SYNC` | Giữ scan và tác vụ đồng bộ dữ liệu dài có notification rõ ràng. |

App không dùng `MANAGE_EXTERNAL_STORAGE`.

## 📂 Cấu trúc thư mục

```text
app/src/main/java/com/youneko/rate/
├── data/          # Entity/DAO Room, repository, import, scan, artwork và worker
├── di/            # Wiring dependency cho Hilt, HTTP và database
├── navigation/    # Navigation graph và route Compose
├── ui/
│   ├── analyze/   # Phân tích audio decode-only và spectrogram
│   ├── artwork/   # Hiển thị cover, palette và cache-aware image loading
│   ├── components/ # Component Material 3 dùng chung và design token
│   ├── coversearch/ # Luồng mở COV ngoài app và import từ gallery
│   ├── export/    # Màn hình backup/export/restore
│   ├── importer/  # Preview và lưu import audio local
│   ├── phase12/   # Collections, tìm kiếm nâng cao và trang nghệ sĩ
│   ├── rate/      # Library, Rate, chi tiết album, Settings và editor
│   └── stats/     # Thống kê và render ảnh chia sẻ
└── res/           # String song ngữ, theme, launcher/splash và vector asset
```

## ⚠️ Giới hạn và an toàn dữ liệu

Youneko Rate! **không phải trình phát nhạc**. App không có luồng playback, không xuất audio, không crawl lyric và không ghi tag vào file audio gốc. Audio chỉ được decode để đọc metadata và phân tích. File nguồn bị mất chỉ được đánh dấu `isMissing`, không xóa rating, review hay credit nhập tay.

Artwork import được lưu riêng trong `filesDir/covers/`, không ghi ngược vào file audio. Backup không chứa file nhạc hoặc token provider. Migration database là migration tường minh; project không dùng `fallbackToDestructiveMigration()`.

## 🌐 Nguồn dữ liệu ngoài

Provider online chỉ được dùng khi người dùng mở tính năng tương ứng. Với COV, app mở website bằng trình duyệt ngoài để người dùng tự tìm và tải ảnh. App không gọi COV `/api/*`, không dùng WebView, proxy request hay giả header. Hãy tôn trọng điều khoản và giới hạn tốc độ của từng nguồn.

## 🐾 Cảm ơn và disclaimer

Cảm ơn hệ sinh thái Android mã nguồn mở và dự án COV vì website công khai để người dùng có thể mở trong trình duyệt. Đây là một dự án beta cá nhân; hành vi, provider và chi tiết UI có thể còn thay đổi.

<p align="center">
  <img src="docs/images/cat_sit.png" width="120" alt="Mèo chibi đang ngồi">
</p>

<p align="center"><sub>Made with 🐾 and too much coffee.</sub></p>

Project được phát hành theo [MIT License](LICENSE).
