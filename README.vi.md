<div align="center">
  <img src="docs/images/logo_paw_purple.png" width="140" alt="Logo chân mèo Youneko Rate màu tím thương hiệu">
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
    <a href="https://github.com/dungzual201/youneko-rate/releases/latest"><img src="https://img.shields.io/github/v/release/dungzual201/youneko-rate?display_name=release" alt="Latest release"></a>
    <img src="https://img.shields.io/badge/100%25%20Offline-6750A4?style=flat&logoColor=white" alt="100% Offline">
    <img src="https://img.shields.io/badge/adaptive-phone%20%26%20tablet-6750A4?style=flat" alt="Tối ưu điện thoại và máy tính bảng">
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

Youneko Rate! là người bạn đồng hành **riêng tư, offline-first** để chấm điểm và lưu lại cảm nhận về thư viện nhạc local. App quét audio trên thiết bị, lưu rating, review, tag và nhật ký nghe tại máy, bổ sung công cụ kiểm tra chất lượng audio và thống kê cá nhân mà không cần tài khoản hay server. Giao diện Material 3 được thiết kế tối ưu cho cả điện thoại và máy tính bảng.

<hr>

## ✨ Tính năng

<img src="docs/images/cat_peek.png" width="90" align="right" alt="Mèo chibi ló lên">

Bốn khu vực chính cùng lớp giao diện thích ứng:

| Khu vực | Khả năng hiện tại |
|---|---|
| **Library** | Quét audio local qua MediaStore và thư mục SAF tùy chọn, enrichment metadata/artwork. Grid hoặc list thích ứng, tìm kiếm, bộ lọc và 6 kiểu sắp xếp cho thư viện lớn. |
| **Rate** | Chấm nửa sao (hoặc thang 10/100 điểm), review album và bài, tag, nhật ký nghe, tiến độ chấm và editor chỉnh metadata/ảnh bìa. |
| **Analyze** | Kiểm tra decode-only với dữ liệu STFT/FFT, sample rate, bit depth, bitrate, cutoff và chỉ số phổ khi nguồn có đủ dữ liệu. |
| **Stats** | Ô tổng quan, album điểm cao nhất, phân bố điểm, trung bình theo ngày nghe, xếp hạng chất lượng/nghệ sĩ/hãng/credit và ảnh chia sẻ render local. |
| **Adaptive UI** | Material 3 với bottom navigation trên điện thoại, navigation rail trên tablet, album detail 2 cột trên tablet lớn, theme sáng/tối và resource đầy đủ EN/VI. |

Giao diện xoay quanh dữ liệu local, Material 3, resource song ngữ, nhận diện launcher/splash hình chân mèo và artwork được lưu trong vùng riêng của app.

### Bản làm mới giao diện

- Library, Rate, Album detail, Analyze, Stats và Settings được xây lại trên cùng một hệ thống layout thích ứng.
- Album detail giữ chấm sao ngay trên từng bài, gom review/tag/nhật ký vào card riêng và chuyển sang bố cục 2 cột cover + tracklist trên màn hình lớn.
- Settings cuộn được toàn bộ và chia nhóm; token nhà cung cấp, nguồn credit, backup và chẩn đoán nằm trong từng card rõ ràng.
- Stats tính trung bình theo ngày nghe của album, hiển thị biểu đồ phân bố thực và render ảnh chia sẻ đồng bộ thương hiệu.
- Toàn bộ chữ hiển thị, thông báo validation và nhãn thang điểm đã được bản địa hoá tiếng Anh/tiếng Việt.

<hr>

## 📸 Ảnh chụp màn hình

Các ảnh dưới đây được render trực tiếp từ mã Compose ở 411 dp (điện thoại), 800 dp (tablet) và 1200 dp (tablet lớn). Đây là preview giao diện, không phải ảnh chụp thiết bị thật; có thể thay bằng ảnh chụp máy thật bất cứ lúc nào.

| Điện thoại · Library | Điện thoại · Chi tiết album |
|---|---|
| <img src="docs/images/redesign_phone_library.png" width="260" alt="Library mới trên điện thoại"><br><sub>Library · grid thích ứng và bộ lọc nhanh</sub> | <img src="docs/images/redesign_phone_album_detail.png" width="260" alt="Chi tiết album mới trên điện thoại"><br><sub>Album detail · chấm sao và ghi chú ngay trên bài</sub> |
| Điện thoại · Rate | Điện thoại · Stats |
|---|---|
| <img src="docs/images/redesign_phone_rate.png" width="260" alt="Tab Rate trên điện thoại"><br><sub>Rate · thao tác nhanh và tiến độ</sub> | <img src="docs/images/redesign_phone_stats.png" width="260" alt="Tab Stats trên điện thoại"><br><sub>Stats · tổng quan và phân bố điểm</sub> |
| Điện thoại · Analyze | Ảnh chia sẻ |
|---|---|
| <img src="docs/images/redesign_phone_analyze.png" width="260" alt="Tab Analyze trên điện thoại"><br><sub>Analyze · kiểm tra decode-only</sub> | <img src="docs/images/redesign_share_card.png" width="260" alt="Ảnh thống kê được render"><br><sub>Share card · render hoàn toàn trên máy</sub> |
| Điện thoại · Sửa album | Điện thoại · Dark theme |
|---|---|
| <img src="docs/images/redesign_phone_album_editor.png" width="260" alt="Editor album trên điện thoại"><br><sub>Editor · metadata, ảnh bìa và tracklist</sub> | <img src="docs/images/redesign_phone_dark_library.png" width="260" alt="Library ở dark theme"><br><sub>Dark theme · bảng màu thương hiệu</sub> |
| Tablet · Library | Tablet · Settings |
|---|---|
| <img src="docs/images/redesign_tablet_library.png" width="260" alt="Library trên tablet với navigation rail"><br><sub>Tablet · navigation rail</sub> | <img src="docs/images/redesign_tablet_settings.png" width="260" alt="Settings trên tablet"><br><sub>Settings · chia nhóm và cuộn được</sub> |

<p align="center">
  <img src="docs/images/redesign_large_tablet_album_detail.png" width="820" alt="Chi tiết album 2 cột trên tablet lớn"><br>
  <sub>Tablet lớn · album detail 2 cột với cover, ghi chú và tracklist</sub>
</p>

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
| Layout thích ứng | Breakpoint theo chiều rộng cửa sổ, `NavigationRail` từ 600 dp, bottom navigation trên điện thoại, album detail 2 cột từ 840 dp |

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
├── data/           # Entity/DAO Room, repository, import, scan, artwork và worker
├── di/             # Wiring dependency cho Hilt và database
├── navigation/     # Navigation graph Compose thích ứng và route
├── ui/
│   ├── adaptive/   # Helper kích thước cửa sổ, padding và số cột thích ứng
│   ├── analyze/    # Phân tích audio decode-only và giao diện spectrogram
│   ├── artwork/    # Hiển thị cover, palette và image loading có cache
│   ├── components/ # Top bar, card, chấm sao và progress Material 3 dùng chung
│   ├── coversearch/# Luồng chọn ảnh bìa
│   ├── export/     # Backup, restore, export CSV/JSON và collage chia sẻ
│   ├── importer/   # Preview và lưu import audio local
│   ├── media/      # Onboarding quyền và tiến trình quét
│   ├── musicbrainz/# Tìm metadata trực tuyến
│   ├── phase12/    # Collection, trang nghệ sĩ và tìm kiếm nâng cao
│   ├── rate/       # Library, Rate, album detail, editor, Settings, chấm sao
│   └── stats/      # Thống kê và render ảnh chia sẻ
└── res/            # String song ngữ, theme, launcher/splash và vector asset
```

</details>

<hr>

## 🔒 Riêng tư

> **Riêng tư theo thiết kế:** rating, review và metadata thư viện local nằm trên thiết bị. App không yêu cầu tài khoản, không có quảng cáo hay analytics, và không cần server cho việc sử dụng local.

Artwork được import lưu trong thư mục riêng `filesDir/covers/` và không ghi vào file audio gốc. File nguồn bị mất chỉ được đánh dấu `isMissing`, không xóa rating, review hoặc dữ liệu nhập tay. Migration database là migration tường minh; project không dùng `fallbackToDestructiveMigration()`.

<hr>

## ⚠️ Giới hạn

Đây là dự án beta cá nhân. App **không phát nhạc**, không ghi tag vào file audio gốc và không tải lyrics online. Lyrics nhúng trong tag vẫn có thể được đọc. Backup/restore bao gồm rating, review, tag, nhật ký nghe và credit thủ công; không bao gồm file nhạc hay API token.

Ảnh ở trên là preview Compose, không phải ảnh chụp thiết bị thật, và workspace chưa có số đo hiệu năng trên máy thật. Hành vi UI có thể còn thay đổi khi beta được kiểm thử trên phần cứng thực tế. Xem `docs/PROGRESS.md` để đọc sổ trạng thái dự án.

<hr>

## 🐾 Cảm ơn

Cảm ơn hệ sinh thái Android mã nguồn mở và mọi người đã thử nghiệm dự án beta cá nhân này. Hành vi, luồng dữ liệu local và chi tiết UI có thể thay đổi khi các giai đoạn chưa hoàn tất được đánh giá tiếp.

<p align="center">
  <img src="docs/images/cat_sit.png" width="120" alt="Mèo chibi đang ngồi">
</p>

Project được phát hành theo [MIT License](LICENSE).
