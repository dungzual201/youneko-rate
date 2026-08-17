# Báo cáo giai đoạn 2 — Rate & Review

## Kết quả tổng quan

Giai đoạn 2 đã triển khai xong trụ cột Rate & Review theo SPEC, chưa bắt đầu giai đoạn 3. Ứng dụng hiện có thể dùng hàng ngày để thêm album/bài lẻ, lưu dữ liệu local, chấm sao trực tiếp trên track, viết review, xem điểm album, tìm kiếm local, sắp xếp/lọc thư viện và quay lại các album đang chấm dở.

Code phase 2 nằm trong commit `72c667666e3074444dec1aba0eabf2333353c127`, sau đó tài liệu hoàn tất được commit riêng. Repository vẫn là private tại [dungzual201/youneko-rate](https://github.com/dungzual201/youneko-rate).

## Những gì đã triển khai

| Khu vực | Nội dung |
|---|---|
| Nhập dữ liệu | Album thủ công với tên, artist autocomplete/tái sử dụng Artist đã có, năm, loại ALBUM/EP/SINGLE/COMPILATION, genre chip dạng comma-separated, ngày nghe và chọn cover URI bằng SAF; bài lẻ qua dialog riêng |
| Tracklist | Thêm/xoá track, thêm nhanh N dòng, long-press drag reorder qua `detectDragGesturesAfterLongPress`, đánh lại `trackNumber` khi lưu và giữ draft bằng `SavedStateHandle` |
| Rating | `StarRatingBar` 0.5–5.0, tap/drag, haptic, animation scale, long-press clear; rating trực tiếp trên từng track; highlight/skip |
| Review | Review album/track nhiều dòng, expand/collapse, giới hạn ký tự, counter và debounce auto-save 800 ms |
| Score | `CalculateAlbumScoreUseCase` thuần Kotlin; bỏ qua track chưa chấm, simple/weighted average, fallback duration thiếu, manual override và HALF_UP 2 decimals |
| Album detail | Cover placeholder/URI, score/progress, review, tracklist, favorite, manual override, xoá có confirm và cascade Room |
| Library | Room FTS4 local-first, debounce 400 ms, grid/list, sort mới/điểm/tên/năm/ngày nghe, favorite/unfinished filters và persistence DataStore |
| Data/DB | Room schema v2, foreign keys `CASCADE`, migration v1→v2 không destructive, artist/album/track/FTS DAO, repository transaction |
| Ngôn ngữ/a11y | `values/strings.xml` tiếng Việt mặc định và `values-en/strings.xml`, icon button content descriptions, StarRatingBar semantics |

## Mô tả giao diện để duyệt trước giai đoạn 3

### Library

Màn Library có TopAppBar của app và bottom navigation bốn tab. Ngay dưới thanh tiêu đề là search bar bo góc chiếm phần lớn chiều ngang, có icon tìm kiếm và placeholder “Tìm album, nghệ sĩ, bài hát hoặc review”. Bên phải có nút mở bottom sheet filter và nút chuyển grid/list. Dòng điều khiển tiếp theo hiển thị số album và menu sort.

Ở chế độ grid, mỗi album là một card bo góc lớn: cover placeholder màu tím pastel hoặc cover URI, tên album tối đa hai dòng, artist, điểm dạng `4.27★` và tiến độ `9/12`. Ở list, cover nhỏ nằm bên trái, thông tin nằm giữa và favorite icon nằm bên phải. Tapping card mở detail. Khi thư viện trống, mascot mèo nằm giữa với lời mời “Thêm album”; khi search/filter không khớp, empty state đổi thành “Không có kết quả phù hợp” và gợi ý xoá filter.

Bottom sheet Filters hiện các chip `Chỉ yêu thích` và `Chưa chấm xong`, cùng nút `Xoá bộ lọc`. Sort menu có `Mới thêm`, `Điểm cao đến thấp`, `Điểm thấp đến cao`, `Tên A–Z`, `Năm phát hành` và `Ngày nghe`. Các lựa chọn được lưu DataStore nên quay lại từ detail không làm mất trạng thái.

### Album detail

Màn detail ẩn bottom navigation để dành toàn bộ chiều cao cho nội dung. Thanh đầu có back, tiêu đề, favorite và menu xoá. Cover lớn nằm phía trên, tiếp theo là tên album, artist, điểm nổi bật và tiến độ số track đã chấm. Nếu có override, màn hình hiển thị đồng thời `Điểm thủ công: 4.50★ (avg 4.27)` và nút xoá override.

Review album là một vùng mở rộng/thu gọn có TextField nhiều dòng và counter ký tự. Bên dưới là tracklist. Mỗi row hiển thị số track, tên bài và StarRatingBar ngay trên cùng một hàng; hàng dưới có toggle highlight, toggle skip và nút mở review. Review track auto-save sau 800 ms. StarRatingBar dùng màu vàng cho điểm đã chọn, sao mờ cho trạng thái chưa chấm; long-press đưa track về trạng thái chưa chấm chứ không biến thành 0.

Menu xoá yêu cầu xác nhận trước khi xoá. Room foreign key cascade xoá track/credit/audio-analysis liên quan thay vì để orphan record. Detail có empty state riêng nếu album chưa có track.

Không có screenshot emulator được nhúng trong báo cáo này vì sandbox hiện không chạy Android emulator; mô tả trên là trạng thái UI thực tế của các composable đã build. APK debug có thể cài trên thiết bị như giai đoạn 1 để duyệt trực tiếp.

## Kiểm thử và build

Local verification đã PASS:

```text
./gradlew clean assembleDebug testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon
BUILD SUCCESSFUL in 1m 11s
```

Bộ unit test `CalculateAlbumScoreUseCaseTest` gồm 10 test, bao phủ album chưa chấm trả `null`, chấm một phần, chấm hết, 0.5 sao, weighted average, thiếu duration fallback, manual override, rounding `4.125 → 4.13` và album một track. `RateDaoTest` trong `androidTest` bao phủ FTS4 review search và cascade delete. Instrumentation source đã compile thành công; connected test cần Android emulator/device nên chưa chạy trong sandbox.

APK local nằm ở `app/build/outputs/apk/debug/app-debug.apk` sau build. Workflow CI debug đã được giữ nguyên để chạy `assembleDebug`, `testDebugUnitTest` và upload APK/test reports; run mới sau commit tài liệu sẽ được xác minh trước khi bàn giao cuối cùng.

## Giới hạn còn lại

Phase 2 chưa làm import tag file local, network metadata, MusicBrainz, provider phụ, credits, audio decode/FFT, backup, stats, onboarding hay open-source license screen. Các phần này vẫn giữ `TODO` trong roadmap và sẽ không được coi là đã hoàn tất. Chưa chuyển sang phase 3.
