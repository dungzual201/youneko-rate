# Báo cáo FIX_ROUND4_COVERSEARCH_GRADIENT_ANALYZE

Báo cáo này ghi nhận các thay đổi Round4 trên nền `a8f01da`. Phạm vi chỉ gồm UI và network layer cho cover search, gradient Album Detail và luồng chọn tệp của Analyze. Không thay đổi verdict, STFT, codec, cutoff, scan, backup, Room migration hay dữ liệu tag của file nhạc.

## 1. Nhật ký 14 commit theo đúng thứ tự

| # | Commit | Trạng thái |
|---:|---|---|
| 1 | `fix(cover): use POST api search voi headers` | Đã hoàn thành: POST `/api/search`, singleton `X-Session` 32 ký tự hex, các header bắt buộc và fallback Referer/Origin. |
| 2 | `fix(cover): parse NDJSON tung dong` | Đã hoàn thành: đọc streaming từng dòng NDJSON và phân loại cover/source/count/error/done. |
| 3 | `fix(cover): surface HTTP status va body loi` | Đã hoàn thành: hiển thị HTTP status/body thật, parse lỗi JSON 400 theo field và bỏ qua `query`; thêm test hồi quy. |
| 4 | `feat(cover): load source list from api info` | Đã hoàn thành: lấy metadata nguồn từ `/api/info`, cache 24 giờ, không hardcode danh sách nguồn. |
| 5 | `fix(cover): disable source sai country` | Đã hoàn thành: disable nguồn không tương thích country hoặc không hỗ trợ query `search`. |
| 6 | `feat(cover): enforce source limit va preset` | Đã hoàn thành: giới hạn theo `activeSourceLimit`, mặc định country `us`, preset 9 nguồn thân thiện với nhạc Việt. |
| 7 | `feat(cover): stream ket qua va huy request` | Đã hoàn thành: emit kết quả ngay khi nhận được, hủy request cũ, timeout đọc 120 giây, call timeout tắt và limiter tối đa một request mỗi giây. |
| 8 | `feat(cover): download resize va apply cover` | Đã hoàn thành: tải trực tiếp có lỗi hotlink có thể hành động, resize cạnh dài tối đa 1500 px, JPEG q90, lưu trong thư mục riêng của app; không ghi tag file nhạc. |
| 9 | `fix(ui): transparent top app bar tren gradient` | Đã hoàn thành: TopAppBar trong Album Detail trong suốt để hòa vào gradient edge-to-edge. |
| 10 | `fix(ui): adapt status bar theo palette` | Đã hoàn thành: `enableEdgeToEdge`, màu chữ và biểu tượng status bar được chọn theo độ tương phản palette. |
| 11 | `feat(analyze): extended FAB va source bottom sheet` | Đã hoàn thành: Extended FAB góc dưới phải, tự thu khi cuộn, bottom sheet ba nguồn, empty state và tooltip cho shortcut folder. |
| 12 | `feat(analyze): pick library va recent files` | Đã hoàn thành: picker thư viện có tìm kiếm, picker thiết bị, năm file gần đây trong DataStore, và long-press album trong Library mở lựa chọn bài để phân tích rồi điều hướng sang Analyze. |
| 13 | `fix(analyze): FlowRow for control chips` | Đã hoàn thành: các chip điều khiển spectrogram dùng FlowRow, tránh cắt mất nút Reset; bổ sung khoảng an toàn dưới cùng cho danh sách. |
| 14 | `test: cover search e2e + contrast measurements + screenshots` | Commit báo cáo và bằng chứng Round4; các bằng chứng cần thiết bị được đánh dấu rõ `CHƯA LÀM`. |

## 2. Sáu bằng chứng bắt buộc

### F1 — A0: HTTP status và body trước khi sửa

Đã thực hiện trước mọi thay đổi Round4. Request thực tế:

```text
POST https://covers.musichoarders.xyz/api/search
X-Session: d14ccaaae60340874f818db8880b0700
X-Page-Referrer: <empty>
X-Page-Query: <empty>
Accept: application/x-ndjson
Content-Type: application/json
Body: {"artist":"Trungg I.U","album":"Hải Trình Tan Vỡ","country":"us","sources":["applemusic"]}
```

Kết quả raw:

```text
HTTP_STATUS:401
HTTP/2 401
content-type: text/plain
server: cloudflare
x-trace: e4fdadafe8c64f1fb313e43ddd65c88d
--- RAW BODY ---
<empty, 0 bytes>
```

Vì không có thiết bị Android/ADB trong sandbox, lệnh `adb logcat` không thể chạy; raw HTTP reproduction bằng curl là bằng chứng thay thế đã thực hiện trước khi sửa.

### F2 — Một dòng NDJSON `type=cover` thật sau khi sửa

**CHƯA LÀM: endpoint vẫn trả HTTP 401 Unauthorized trong lần probe cuối với required headers, nên không có dòng NDJSON `type=cover` hợp lệ để trích dẫn.** Không tạo dữ liệu giả. Probe cuối theo mẫu Faouzia/UNETHICAL lúc `2026-08-22T15:31:52Z` trả `HTTP/2 401`, `content-type: text/plain`, Cloudflare trace `9584fd4bd75b473c82aceb8f1e9b7d3a`, body 93 bytes:

```text
Please do not use the internal API directly. Consult the integrations section on the website.
```

### F3 — Screenshot grid cover có ảnh thật

**CHƯA LÀM: không có thiết bị/emulator/ADB trong sandbox và endpoint live bị 401, nên không thể tạo screenshot grid có ảnh thật.** UI và test MockWebServer cho streaming cover đã được kiểm tra, nhưng đó không phải ảnh chụp live.

### F4 — Contrast ratio của ba màu đại diện

Các giá trị dưới đây là phép tính thực từ helper regression test trên ba màu palette đại diện; chúng xác nhận nhánh chọn màu chữ đạt tối thiểu 4.5:1. Đây **không phải** phép đo từ ba ảnh cover live, vì F2/F3 bị chặn bởi HTTP 401 và thiếu thiết bị.

| Màu dominant | Màu chữ được chọn | Contrast ratio |
|---|---|---:|
| `#101820` | Trắng | `17.8945:1` |
| `#F2C14E` | Đen | `12.5134:1` |
| `#6B2D5C` | Trắng | `9.7445:1` |

Cả ba đều vượt mục tiêu `4.5:1`.

### F5 — Screenshot Album Detail với gradient liền status bar

**CHƯA LÀM: không có thiết bị/emulator/ADB trong sandbox để chụp màn hình thật.** Mã nguồn đã dùng edge-to-edge, TopAppBar trong suốt, gradient tĩnh và status-bar icon mode theo luminance; không tuyên bố đây là bằng chứng hình ảnh phần cứng.

### F6 — Video/GIF FAB → bottom sheet → chọn bài thư viện

**CHƯA LÀM: không có thiết bị/emulator/ADB trong sandbox để quay video/GIF thao tác.** Luồng đã được nối trong mã nguồn gồm Extended FAB, ModalBottomSheet, ô tìm kiếm thư viện và điều hướng URI sang Analyze; chưa có bằng chứng video thật.

## 3. Kiểm chứng tự động

Lệnh bắt buộc đã chạy thành công:

```text
./gradlew assembleDebug testDebugUnitTest lintDebug compileDebugAndroidTestKotlin --no-daemon --max-workers=1 --console=plain
BUILD SUCCESSFUL in 2m 39s
74 actionable tasks: 14 executed, 60 up-to-date
```

APK debug được tạo tại:

```text
app/build/outputs/apk/debug/app-debug.apk
size: 29,562,000 bytes
```

Unit tests đã vượt qua, gồm test HTTP 400 field errors và các invariant Analyze/branding. `lintDebug` cũng vượt qua; không phát hiện lỗi resource parity. Kiểm tra khóa `values/strings.xml` và `values-vi/strings.xml` bằng `comm -3` không trả dòng nào. Forbidden scan trong `app/src/main` không tìm thấy `MediaPlayer`, `ExoPlayer`, `AudioTrack`, `fallbackToDestructiveMigration`, lyrics crawling hay `previewUrl`.

## 4. Giới hạn còn lại

Live MusicHoarders hiện từ chối request trực tiếp bằng HTTP 401 và khuyến nghị dùng khu vực integrations của website. Vì vậy app hiện giữ hành vi trung thực: hiển thị lỗi HTTP/body thật và không giả vờ có kết quả cover. Việc xác nhận hình ảnh, status-bar gradient trên phần cứng, FPS/jank và video thao tác vẫn cần một điện thoại Android thật sau khi endpoint hoặc quyền tích hợp được cấp.
