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

## Mục lục

- [✨ Tính năng](#-tính-năng)
- [📸 Ảnh chụp màn hình](#-ảnh-chụp-màn-hình)
- [🛠️ Công nghệ](#️-công-nghệ)
- [📋 Yêu cầu](#-yêu-cầu)
- [🔨 Build](#-build)
- [🔒 Riêng tư](#-riêng-tư)
- [⚠️ Giới hạn](#️-giới-hạn)
- [🐾 Cảm ơn](#-cảm-ơn)

Youneko Rate! là một người bạn đồng hành **offline-first** để lưu lại cảm nhận về album một cách có hệ thống. App đọc thư viện audio local, lưu rating và review trên thiết bị, đồng thời cung cấp công cụ sắp xếp và kiểm tra dữ liệu mà không giả định các phần beta chưa hoàn tất là đã xong.

<hr>

## ✨ Tính năng

<img src="docs/images/cat_peek.png" width="90" align="right" alt="Mèo chibi ló lên">

App hiện có bốn khu vực chính:

| Khu vực | Khả năng hiện tại |
|---|---|
| **Library** | Quét audio local qua MediaStore và thư mục SAF tùy chọn, sau đó enrichment metadata và artwork local. Có hiển thị tiến trình quét. |
| **Rate** | Chấm điểm thủ công cho bài và album, viết review, quản lý tag, listening log và bộ lọc rating local. |
| **Analyze** | Kiểm tra audio theo chế độ decode-only và dữ liệu STFT/FFT, hiển thị sample rate, bit depth, bitrate cùng chỉ số phổ khi đủ dữ liệu nguồn. |
| **Stats** | Tổng hợp rating và kết quả phân tích đã lưu bằng phân phối, xếp hạng và trình render ảnh chia sẻ local. |

Giao diện xoay quanh dữ liệu local, Material 3, resource song ngữ, nhận diện launcher/splash hình chân mèo và artwork được lưu trong vùng riêng của app.

<hr>

## 📸 Ảnh chụp màn hình

Repository vẫn đang ở giai đoạn beta và chưa có ảnh chụp trên thiết bị thật. Không ảnh nào được tạo, tải từ ngoài hoặc giả mạo. Khi có ảnh, mỗi ô đã dành sẵn cho ảnh rộng 250 pixel kèm chú thích.

| Library | Chi tiết album |
|---|---|
| **Library**<br>**THIẾU ẢNH:** `docs/images/screenshot_library.png`<br><sub>Cần ảnh chụp trên thiết bị thật</sub> | **Chi tiết album**<br>**THIẾU ẢNH:** `docs/images/screenshot_album_detail.png`<br><sub>Cần ảnh chụp trên thiết bị thật</sub> |
| Rate | Analyze |
|---|---|
| **Rate**<br>**THIẾU ẢNH:** `docs/images/screenshot_rate.png`<br><sub>Cần ảnh chụp trên thiết bị thật</sub> | **Analyze**<br>**THIẾU ẢNH:** `docs/images/screenshot_analyze.png`<br><sub>Cần ảnh chụp trên thiết bị thật</sub> |
| Stats | — |
|---|---|
| **Stats**<br>**THIẾU ẢNH:** `docs/images/screenshot_stats.png`<br><sub>Cần ảnh chụp trên thiết bị thật</sub> | **THIẾU ẢNH:** ô thứ hai được để trống có chủ đích |

Hiện chỉ có một ảnh chân mèo. **THIẾU ẢNH:** `docs/images/logo_paw_light.png`, `docs/images/logo_paw_dark.png`, `docs/images/cat_peek_light.png`, `docs/images/cat_peek_dark.png`, `docs/images/cat_sit_light.png`, `docs/images/cat_sit_dark.png`. Trước khi có đủ cặp ảnh, phần `<picture>` dùng `logo_paw.png` hiện có cho cả hai theme.

<hr>

## 🛠️ Công nghệ

Các phiên bản dưới đây được đọc trực tiếp từ `gradle/libs.versions.toml` và `app/build.gradle.kts`.

| Nhóm | Triển khai thực tế |
|---|---|
| Ngôn ngữ và UI | Kotlin `2.4.10`, Jetpack Compose, Compose BOM `2026.08.00`, Material 3 |
| Android build | Android Gradle Plugin `9.3.1`, Gradle wrapper `9.5.0`, compile SDK `37`, min SDK `26`, target SDK `36` |
| Dữ liệu local | Room `2.8.4` dùng KSP và exported schema; DataStore Preferences `1.2.1` |
| Dependency injection | Hilt `2.60.1` và Hilt Navigation Compose `1.4.0` |
| Tác vụ nền | WorkManager `2.11.2` cho scan và các tác vụ local dài hơn |
| Ảnh và artwork | Coil `2.7.0`, AndroidX Palette `1.0.0`, AndroidX ExifInterface `1.3.7` |
| Audio và tag | MediaExtractor, MediaCodec, MediaMetadataRetriever, jaudiotagger `3.0.1`, JTransforms `3.1` |
| AndroidX chính | core-ktx `1.19.0`, core-splashscreen `1.0.1`, Activity Compose `1.13.0`, Navigation Compose `2.9.8` |

Kiến trúc hiện tại là MVVM thực dụng theo hướng repository/data layer. Composable quan sát `StateFlow` từ ViewModel; DAO cung cấp dữ liệu Room, Hilt cấp dependency, DataStore lưu preference/trạng thái scan, còn WorkManager xử lý các tác vụ dài.

<hr>

## 📋 Yêu cầu

| Thiết lập | Giá trị |
|---|---:|
| `minSdk` | 26 |
| `targetSdk` | 36 |
| `compileSdk` | 37 |
| Java/JDK | 17 |
| Kotlin | 2.4.10 |

<details>
<summary>Quyền Android</summary>

| Quyền | Lý do cần quyền |
|---|---|
| `READ_MEDIA_AUDIO` trên Android 13+ | Đọc metadata audio local từ MediaStore. |
| `READ_EXTERNAL_STORAGE` trên Android 12 trở xuống | Đọc thư viện audio local tương ứng trên Android cũ. |
| `INTERNET` | Hỗ trợ các thao tác trực tuyến tùy chọn đã có trong bản beta khi người dùng chủ động yêu cầu. |
| `FOREGROUND_SERVICE` và `FOREGROUND_SERVICE_DATA_SYNC` | Giữ tác vụ dữ liệu local dài có notification rõ ràng. |

App không dùng `MANAGE_EXTERNAL_STORAGE`.

</details>

<hr>

## 🔨 Build

<details open>
<summary>Các bước build</summary>

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

Chạy toàn bộ bộ kiểm tra local:

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug compileDebugAndroidTestKotlin
```

</details>

<details>
<summary>Cấu trúc thư mục</summary>

```text
app/src/main/java/com/youneko/rate/
├── data/          # Entity/DAO Room, repository, import, scan, artwork và worker
├── di/            # Wiring dependency cho Hilt và database
├── navigation/    # Navigation graph và route Compose
├── ui/
│   ├── analyze/   # Phân tích audio decode-only và giao diện spectrogram
│   ├── artwork/   # Hiển thị cover, palette và image loading có cache
│   ├── components/ # Component Material 3 dùng chung
│   ├── importer/  # Preview và lưu import audio local
│   ├── rate/      # Library, Rate, chi tiết album, Settings và editor
│   └── stats/     # Thống kê và render ảnh chia sẻ
└── res/           # String song ngữ, theme, launcher/splash và vector asset
```

</details>

<hr>

## 🔒 Riêng tư

> **Riêng tư theo thiết kế:** rating, review và metadata thư viện local nằm trên thiết bị. App không yêu cầu tài khoản, không có quảng cáo hay analytics, và không cần server cho việc sử dụng local.

Artwork được import lưu trong thư mục riêng `filesDir/covers/` và không ghi vào file audio gốc. File nguồn bị mất chỉ được đánh dấu `isMissing`, không xóa rating, review hoặc dữ liệu nhập tay. Migration database là migration tường minh; project không dùng `fallbackToDestructiveMigration()`.

<hr>

## ⚠️ Giới hạn

Đây là dự án beta. App **không phát nhạc**, không ghi tag audio gốc, không lấy lời bài hát và không có chức năng sao lưu. Đây là các ranh giới có chủ đích, không nên hiểu là cam kết về tính năng tương lai.

Repository chưa có screenshot thiết bị thật, chưa có số đo hiệu năng trên thiết bị trong workspace này và không tuyên bố các giai đoạn đang thực hiện là đã hoàn tất. Xem `docs/PROGRESS.md` để đọc sổ trạng thái thận trọng của dự án.

<hr>

## 🐾 Cảm ơn

Cảm ơn hệ sinh thái Android mã nguồn mở và mọi người đã thử nghiệm dự án beta cá nhân này. Hành vi, luồng dữ liệu local và chi tiết UI có thể thay đổi khi các giai đoạn chưa hoàn tất được đánh giá tiếp.

<p align="center">
  <img src="docs/images/cat_sit.png" width="120" alt="Mèo chibi đang ngồi">
</p>

Project được phát hành theo [MIT License](LICENSE).
