# Báo cáo giai đoạn 0 — Youneko Rate!

## 1. Tóm tắt hiểu biết về dự án

1. Youneko Rate! là ứng dụng Android native viết bằng Kotlin và Jetpack Compose.
2. Ứng dụng offline-first, dùng mạng chỉ để tra cứu/fetch metadata một chiều khi người dùng cho phép; không có tính năng đồng bộ/sync.
3. Trụ cột thứ nhất là Rate & Review cho album, bài hát và bài lẻ.
4. Người dùng chấm từ 0.5 đến 5.0 sao theo bước cấu hình được.
5. Điểm trung bình album lấy từ các bài đã chấm, bỏ qua bài chưa chấm thay vì coi là 0.
6. Điểm có thể tính trung bình đơn giản hoặc có trọng số theo thời lượng.
7. Người dùng được ghi đè điểm album thủ công; điểm gốc và review không bị metadata ghi đè.
8. Thư viện hỗ trợ nhập thủ công, import tag từ file nhạc local và import từ metadata online.
9. Trụ cột thứ hai là Audio Quality Checker kiểu Spek cho file nhạc do người dùng tự chọn.
10. File nhạc chỉ được đọc cục bộ, không copy đi đâu, không upload và không phát trực tuyến.
11. Spectrogram và các chỉ số phải bắt nguồn từ PCM decode thật, sau đó FFT streaming theo block.
12. Kết quả phân tích gồm thông tin codec thật, cutoff, verdict, độ tin cậy và lý do minh bạch.
13. Heuristics chỉ mang tính tham khảo, không được trình bày như bằng chứng tuyệt đối.
14. MusicBrainz là nguồn metadata chính, Cover Art Archive là nguồn ảnh bìa đi kèm.
15. Discogs, Last.fm, Deezer và ListenBrainz Labs là provider phụ, mặc định tắt.
16. Credits chỉ tải khi người dùng chủ động mở; cache để xem lại offline.
17. Room lưu dữ liệu local; UUID giúp merge backup giữa nhiều thiết bị không xung đột.
18. Backup hỗ trợ JSON/ZIP, ảnh bìa, spectrogram, manifest checksum và merge/replace.
19. Giao diện gồm Library, Rate, Analyze, Stats và Settings, có tiếng Việt mặc định cùng English.
20. Quy trình phát triển gồm 12 giai đoạn tuần tự; mỗi giai đoạn phải build/demo, cập nhật tài liệu, commit và push.

## 2. Rủi ro kỹ thuật chính

| Mức độ | Rủi ro | Tác động | Cách giảm thiểu đề xuất |
|---|---|---|---|
| Cao | Decode đa định dạng, đặc biệt ALAC, AIFF, APE, WavPack, TTA, Musepack, WMA và DSD trên Android. | Một số định dạng có thể không được Media3/MediaCodec hỗ trợ đồng nhất giữa thiết bị; tầng FFmpeg/JNI làm tăng kích thước APK, rủi ro ABI và bảo trì. | Giữ interface `AudioDecoder`; ưu tiên Media3 cho tầng 1; kiểm chứng một thư viện FFmpeg prebuilt còn duy trì hoặc tự build JNI chỉ sau khi có test thiết bị/ABI. Định dạng không decode được phải báo lỗi rõ ràng, không fake kết quả. |
| Cao | Hiệu năng FFT và render spectrogram trên máy yếu, đặc biệt file 24-bit/192 kHz dài. | Có thể tốn CPU/RAM, gây nóng máy hoặc hết bộ nhớ nếu xử lý sai. | Streaming theo block, không load toàn file; WorkManager + foreground progress khi cần; benchmark FFT size/overlap; render Bitmap background; giới hạn hàng đợi và huỷ an toàn. |
| Cao | Heuristics phân biệt lossless thật, transcode và hi-res upsample không tuyệt đối. | Master gốc bị giới hạn, codec hiện đại không có cutoff rõ hoặc file có im lặng có thể tạo false positive/negative. | Hiển thị info card bắt buộc; lưu từng chỉ số và lý do; dùng `KHÔNG XÁC ĐỊNH` khi dữ liệu không đủ; không coi verdict là bằng chứng pháp lý. |
| Cao | Thay đổi/thiếu field và response lớn của MusicBrainz, đặc biệt credits. | Request tổng hợp có thể lỗi, chậm hoặc thiếu work-level credits. | Nullable DTO; cache-then-network; rate limit token bucket; retry 503; fallback lookup recording/work; credits lazy; không crash khi thiếu field. |
| Trung bình | Tôn trọng rate limit của MusicBrainz và các provider phụ. | Bị 503/block hoặc làm giảm chất lượng dịch vụ nếu gọi quá nhanh. | Interceptor riêng từng provider; MusicBrainz 1 request/giây trung bình; Discogs throttle theo quota/header; User-Agent bắt buộc; ETag/If-Modified-Since. |
| Trung bình | Dedupe và merge backup giữa thiết bị. | Có thể tạo album trùng hoặc hỏi xung đột khó hiểu nếu metadata khác nhau. | Ưu tiên UUID/MBID; fuzzy match có ngưỡng; preview trước restore; không ghi đè điểm/review; hỏi người dùng khi xung đột. |
| Trung bình | SAF URI permission và khả năng truy cập file lâu dài. | URI có thể mất quyền hoặc provider storage có hành vi khác nhau. | Persistable URI permission; lưu URI thay vì copy audio; kiểm tra quyền trước phân tích; thông báo lỗi rõ ràng. |
| Thấp | Kích thước APK và giới hạn CI khi bổ sung native FFmpeg/JNI. | Build/release lâu hơn, cần quản lý ABI và NDK chính xác. | Chỉ thêm NDK khi thật sự cần; dùng `android-actions/setup-android` và khai báo version trong Gradle; cân nhắc tách codec mở rộng. |

## 3. Câu hỏi cần người dùng quyết định thêm

1. Khi tầng 1 không decode được định dạng mở rộng, ưu tiên thư viện FFmpeg prebuilt nào nếu có nhiều lựa chọn còn duy trì? Có chấp nhận tăng kích thước APK để hỗ trợ tối đa định dạng không?
2. Có cần phát hành bản APK universal hay chấp nhận APK theo ABI để giảm kích thước khi tích hợp JNI/FFmpeg?
3. Với provider phụ, người dùng muốn ưu tiên thứ tự mặc định nào sau MusicBrainz/Cover Art Archive?
4. Có muốn bắt buộc nhập Discogs Personal Access Token/Last.fm API key ngay khi bật provider, hay chỉ cảnh báo và cho phép tính năng suy giảm?
5. Khi phân tích một thư mục rất lớn, giới hạn số file trong một job và chính sách giữ kết quả cũ nên là bao nhiêu?
6. Với file không decode được ở cả hai tầng, có cần cho phép người dùng lưu metadata tag thủ công mà không tạo `AudioAnalysis` không?
7. Có cần mã hóa backup hoặc chỉ dùng ZIP/JSON thuần dễ đọc như SPEC hiện quy định?
8. License phát hành của phần mã nguồn ứng dụng chưa được chốt; cần quyết định trước khi công bố source rộng hơn.

## 4. Thư viện thay thế so với SPEC

Ở giai đoạn 0 chưa khóa thêm thư viện nào ngoài các hướng đã nêu trong SPEC. Quyết định rõ ràng là **không dùng `ffmpeg-kit`** vì đã retired/archived; tầng decode mở rộng sẽ được chọn sau khi xác minh thư viện còn duy trì và có bằng chứng build/runtime. JTransforms hoặc TarsosDSP cũng chưa được chốt tuyệt đối trước benchmark thực tế.

## 5. Kết quả CI/CD giai đoạn 0

Workflow `Android Build` đã được GitHub nhận đúng từ nhánh `main`, dùng checkout, Temurin JDK 17 và Gradle setup/cache. Run [Android Build #2](https://github.com/dungzual201/youneko-rate/actions/runs/32046899029) đã chạy đến bước `Assemble debug APK` nhưng **FAIL** với log thực tế:

```text
/home/runner/work/_temp/14203bb4-47c3-4082-a3d3-9e1e32409ff7.sh: line 1: ./gradlew: No such file or directory
Error: Process completed with exit code 127.
```

Đây là kết quả được dự đoán ở giai đoạn 0 vì chưa được phép tạo Android app code, Gradle wrapper hoặc `build.gradle.kts`; workflow đã sẵn sàng để tự chạy lại và build APK sau giai đoạn 1. Không có APK artifact hoặc test report ở run này.
