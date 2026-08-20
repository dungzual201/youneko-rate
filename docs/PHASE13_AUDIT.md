# Phase 13 audit — 2026-08-18

## A — Analyze

| Hạng mục | Trạng thái | Bằng chứng |
|---|---|---|
| Progress payload file/index/total/step/stepProgress | Đã triển khai | `AudioAnalysisWorker` phát WorkManager progress và notification foreground |
| FFT frame progress mỗi 25 frame | Đã triển khai | Engine callback `onProgress`/`onFrameProgress`, worker publish có cancellation guard |
| Foreground notification + action Huỷ | Đã triển khai | `AudioAnalysisCancelReceiver`, `cancelUniqueWork`, `isStopped` trong decode/FFT/finally |
| Analyze Running Card/Snackbar/disable khi chạy | Đã triển khai | `AnalyzeUiState`, `WorkInfo.toAnalyzeUiState()`, Running Card và cancel feedback |
| bin→Hz/verdict fallback | Đã triển khai | Công thức đúng; silence/empty có lý do; decoded non-silent đi vào bucket verdict thay vì UNKNOWN |
| cutoff/slope | Đã triển khai | reference 1–4 kHz, ref−50 dB, 6 stable bins, percentile frame cutoff, slope 2 kHz |

## B — Credits

| Hạng mục | Trạng thái | Bằng chứng |
|---|---|---|
| MusicBrainz track scope | Đã triển khai | recording/work credits dùng `trackId`, release credits dùng `albumId`; cache `credits:v2` |
| Embedded/file-tag source | Đã triển khai | `file_tags` được đọc và persist ngay khi import |
| Discogs/Genius | Đã triển khai | provider riêng, cache/limiter riêng, Discogs User-Agent, settings toggle/token |
| Merge/dedupe | Đã triển khai | roleGroup, source priority, tên có dấu, sourceProvider hợp nhất; manual preservation |
| Album credits union | Đã triển khai | DAO query album-scope + mọi track thuộc album, UI nhóm theo track title |
| Empty state | Đã triển khai | Có nút release khác, bật nguồn trong Settings và mở MusicBrainz; không footer MusicBrainz cố định |

## C — Cover

| Hạng mục | Trạng thái | Bằng chứng |
|---|---|---|
| Providers | Đã triển khai | embedded → CAA → iTunes → Deezer → Discogs; không gọi covers.musichoarders.xyz |
| Match validation | Đã triển khai | `CoverMatch`: title ≥0.85, artist ≥0.80, track count/year điều kiện thứ ba |
| Resolution/storage | Đã triển khai | chọn ảnh hợp lệ lớn nhất ≥500 px, JPEG quality 92 trong `filesDir/covers` |
| Provenance | Đã triển khai | `coverSource`, `coverWidth`, `coverUpdatedAt`; Room migration 9→10, schema `10.json` |
| Manual cover | Đã triển khai | editor/import picker gán `Manual`, worker serialize provenance, repository không overwrite existing cover |
| Picker | Đã triển khai | grid hiển thị provider, kích thước, score; chọn thiết bị và URL thủ công |

## UI/other

Track bottom sheet hiện đúng 5 mục: Xem credits, Viết đánh giá, Chấm điểm, Phân tích chất lượng và Xem trên MusicBrainz; MusicBrainz bị disable khi thiếu recording MBID. Track title/action text cho phép nhiều dòng; không thêm playback capability.

## Required verification

Focused AudioAnalysis/CoverArt/Phase6Credits tests đã chạy; full `assembleDebug`, `testDebugUnitTest`, `lintDebug` và CI cần chạy lại sau các thay đổi phase 13. Sandbox không có emulator/adb, nên manual screenshots/device tests phải do người dùng xác nhận; Phase 9 không được bắt đầu trước các ảnh đó.
