Nguồn chỉ thị: /home/ubuntu/upload/FIX_COVERART_UI_AND_PHASE8.md

Cover Art evidence: release-group 09196194-c3d5-4864-b111-9cd3f9bf2daa trả HTTP 200 tại https://coverartarchive.org/release-group/09196194-c3d5-4864-b111-9cd3f9bf2daa với 2 ảnh Front approved và thumbnails 250/500/1200/small/large.

A: CoverArt Archive cần OkHttpClient riêng, followRedirects(true), followSslRedirects(true), không User-Agent MusicBrainz/token bucket. List URL fallback release-group/front-250 rồi release/front-250; download front-500 rồi front-250, 404 là NotFound/placeholder. Coil singleton ImageLoader, crossfade, placeholder/error drawable mèo, memory/disk cache. Lưu ảnh import vào filesDir/covers/{albumId}.jpg; detail local-first; menu Tải lại ảnh bìa; local file embedded artwork ưu tiên.

B: Loading search phải centered trong vùng nội dung; append Paging loader item 56dp; preview/loading/import không nằm cuối màn hình hoặc bị bottom navigation che. Skeleton 3–5 dòng là tùy chọn.

C: Badge đầy đủ MusicBrainz, Credit footer nguồn + Mở trang, tracklist compact với menu actions/bottom sheet, Credits dòng người/vai trò/nhạc cụ gộp, group count và tối đa 3 nhóm mở.

D Phase 8 chỉ bắt đầu sau A–C và ảnh chụp chứng minh: MediaExtractor/MediaCodec streaming PCM; 3 đoạn 30s ở 25/50/75%, FFT 4096 Hann hop 2048 trên Dispatchers.Default; cutoff/rolloff/clipping/true peak/DR; WorkManager foreground cancellable; tests audio fixtures.
