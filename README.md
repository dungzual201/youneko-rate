# Youneko Rate!

Ứng dụng Android native **offline-first** để chấm điểm và viết review cho album/bài hát, đồng thời phân tích phổ tần từ file nhạc do người dùng chọn để tham khảo chất lượng audio. Repository này đang ở bước chuẩn bị giai đoạn 0; **chưa có mã nguồn ứng dụng Android**.

## Phạm vi và nguyên tắc

Ứng dụng có hai trụ cột: **Rate & Review**, trong đó điểm album được tính từ các track đã chấm và không coi track chưa chấm là 0; và **Audio Quality Checker**, trong đó phổ tần phải được tính từ PCM decode thật. Ứng dụng không phát nhạc trực tuyến, không stream, không tải nhạc và không upload file âm thanh. File nhạc chỉ được đọc cục bộ để phân tích.

Dữ liệu người dùng, gồm điểm số và review, là tối cao và không bị ghi đè khi refresh metadata. Các chức năng rating, review và audio analysis phải hoạt động offline. Network chỉ phục vụ metadata từ các API công khai được bật trong Settings và có công tắc tắt hoàn toàn. Ứng dụng không analytics, tracker, telemetry và không gửi dữ liệu người dùng đi đâu.

## Stack mục tiêu

Dự án sẽ dùng Kotlin, Jetpack Compose, Material 3, minSdk 26, targetSdk 36, Gradle Kotlin DSL, JDK 17, version catalog, MVVM/Clean-ish, Hilt, Coroutines/Flow, Navigation Compose, Room, DataStore, WorkManager, Paging 3, Retrofit, OkHttp, kotlinx.serialization và Coil.

Không dùng `ffmpeg-kit`. Giải mã audio sẽ được bọc sau `AudioDecoder`: tầng mặc định dùng Media3 decoders cùng `MediaExtractor`/`MediaCodec`; tầng mở rộng sẽ chỉ được chọn sau khi xác minh thư viện FFmpeg prebuilt AAR còn được duy trì hoặc phương án tự build FFmpeg + JNI. Không khai báo dependency/version chưa được xác minh.

## Dữ liệu và nguồn metadata

MusicBrainz là nguồn chính, luôn bật; Cover Art Archive được dùng để lấy ảnh bìa. Provider phụ được ưu tiên theo thứ tự Discogs → Deezer → Last.fm → ListenBrainz Labs, tất cả mặc định tắt và chỉ bật khi người dùng chủ động cấu hình theo điều kiện của từng dịch vụ. Đây là tra cứu/fetch metadata một chiều, không có tính năng đồng bộ/sync; backup chỉ do người dùng chủ động xuất/nhập. App chỉ dùng API công khai chính thức, không scrape HTML, không dùng Spotify/Apple Music/YouTube Music API để xây dựng thư viện offline.

MusicBrainz phải có User-Agent riêng theo format trong `SPEC.md`, token bucket capacity 5 với refill 1 token/giây, retry 503 có exponential backoff + jitter và tôn trọng cache validator. Credits chỉ được tải khi người dùng bấm xem.

## Trạng thái và tài liệu

- [`SPEC.md`](SPEC.md): bản đặc tả đầy đủ, là nguồn yêu cầu duy nhất.
- [`PROGRESS.md`](PROGRESS.md): trạng thái 12 giai đoạn và commit chính thức.
- [`DECISIONS.md`](DECISIONS.md): các quyết định kỹ thuật, lý do và điểm đang chờ xác minh.

Làm việc tuần tự theo roadmap trong mục 10 của SPEC. Cuối mỗi giai đoạn phải build được, demo được, cập nhật `PROGRESS.md`/`DECISIONS.md`, commit và push.

## Build local

Sau khi project Android được khởi tạo ở giai đoạn 1, môi trường yêu cầu JDK 17 và Android SDK phù hợp với `compileSdk/targetSdk`. Từ thư mục repository, chạy:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

APK debug dự kiến nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Có thể mở repository bằng Android Studio phiên bản tương thích với Gradle/AGP được khai báo trong version catalog. Nếu sandbox không có đủ Android SDK để build, GitHub Actions là đường build chính thức; log CI và artifact APK được xem trong tab **Actions**.

## CI build debug

Workflow [`.github/workflows/android-build.yml`](.github/workflows/android-build.yml) chạy khi push lên `main`, khi có pull request và khi kích hoạt thủ công. Workflow dùng Ubuntu, checkout v4, Temurin JDK 17, Gradle setup/cache, chạy `assembleDebug` và `testDebugUnitTest`, sau đó upload APK debug cùng báo cáo test trong 30 ngày.

## Release và ký APK

Workflow [`.github/workflows/release.yml`](.github/workflows/release.yml) chạy khi push tag bắt đầu bằng `v`, build release, ký bằng keystore lấy từ GitHub Secrets và tạo GitHub Release. Tuyệt đối không commit keystore, file `.jks`, mật khẩu hoặc secret vào repository.

Discogs Personal Access Token, Last.fm API key và mọi credential khác chỉ nằm trong DataStore, **không bao giờ được serialize vào `data.json`, `manifest.json` hoặc ZIP backup**. Màn hình Export phải hiển thị cảnh báo này; ZIP có mật khẩu chỉ là hướng mở rộng về sau.

### Tự tạo keystore

Thực hiện trên máy cá nhân, thay các giá trị trong dấu ngoặc bằng thông tin của bạn:

```bash
keytool -genkeypair -v \\
  -keystore youneko-rate-release.jks \\
  -alias <key-alias> \\
  -keyalg RSA -keysize 2048 -validity 10000
```

Encode keystore thành một dòng base64 để dán vào GitHub Secret:

```bash
base64 -w 0 youneko-rate-release.jks > keystore.base64.txt
```

Tạo bốn Secrets trong **Repository Settings → Secrets and variables → Actions**:

| Secret | Giá trị |
|---|---|
| `KEYSTORE_BASE64` | Toàn bộ nội dung một dòng của `keystore.base64.txt` |
| `KEYSTORE_PASSWORD` | Mật khẩu keystore |
| `KEY_ALIAS` | Alias đã dùng khi tạo keystore |
| `KEY_PASSWORD` | Mật khẩu của key |

Xóa `keystore.base64.txt` khỏi máy dùng chung sau khi đã dán secret. Có thể kiểm tra release bằng cách tạo tag, ví dụ `git tag v0.1.0 && git push origin v0.1.0`, sau khi workflow và signing config của app đã hoàn thiện.

## Legal & Privacy

Metadata album, bài hát và credits được cung cấp bởi [MusicBrainz](https://musicbrainz.org/) theo CC0 và [Cover Art Archive](https://coverartarchive.org/). Các provider phụ, nếu bật, phải được hiển thị cùng liên kết Terms of Use tương ứng. Ứng dụng chỉ lưu metadata và nội dung người dùng tự viết; không lưu trữ, phát hoặc phân phối nội dung âm thanh. File nhạc chỉ được đọc để phân tích và không rời khỏi máy.

Màn Settings > About & Data Sources phải có công tắc **Chế độ hoàn toàn offline**, cam kết không analytics/tracker và thông tin nguồn dữ liệu. Phân tích phổ là tham khảo, không phải bằng chứng tuyệt đối; verdict phải hiển thị lý do và giới hạn của heuristic.

## Quy trình đóng góp nội bộ

Trước mỗi giai đoạn, đọc lại `SPEC.md` và `PROGRESS.md`. Sau mỗi giai đoạn, chỉ chuyển trạng thái sang `DONE` khi đã build/demo, cập nhật tài liệu, commit và push. Commit message dùng dạng `feat(phase-N): mô tả ngắn`, ví dụ `feat(phase-4): musicbrainz search & throttle`.

## License và phạm vi phát hành

Chưa chốt license phát hành của phần mã nguồn ứng dụng. `jaudiotagger` là LGPL và phải dynamic-link; PoC FFmpeg ở phase 12 ưu tiên bản LGPL, phải ghi rõ codec bị loại nếu ràng buộc GPL. Mọi dependency phải có license inventory và app sẽ có màn Open source licenses ở phase 11. Việc sử dụng dữ liệu và API bên thứ ba phải tuân thủ điều khoản của từng nguồn; phần attribution bắt buộc trong app được mô tả trong SPEC.
