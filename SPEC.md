# DỰ ÁN: Xây dựng ứng dụng Android "Youneko Rate!"

Bạn là senior Android engineer. Hãy xây dựng một ứng dụng Android hoàn chỉnh, build được ra APK,
tên là **"Youneko Rate!"** — app offline-first gồm hai trụ cột:

1. **Rate & Review**: chấm sao và viết nhận xét cho nhạc/album đã nghe, tự động tính điểm trung bình
   album dạng số thập phân từ điểm của từng bài.
2. **Audio Quality Checker**: phân tích phổ tần số (kiểu Spek) từ file nhạc người dùng tự đưa vào, để
   xác minh file có thực sự là Lossless / Hi-Res hay chỉ là bản transcode từ lossy.

Kèm theo đó là module tra cứu metadata từ các nền tảng dữ liệu mở (có thanh tìm kiếm và trang credits),
và hệ thống xuất/nhập dữ liệu để người dùng tự quản lý bản sao lưu.

---

## 0. NGUYÊN TẮC BẤT DI BẤT DỊCH (áp dụng cho toàn bộ dự án)

- **Offline-first**: mọi chức năng chấm điểm, xem lại, và phân tích âm thanh phải hoạt động 100% không cần
  mạng. Mạng chỉ dùng để tra metadata, và người dùng tắt được hoàn toàn.
- **KHÔNG phát nhạc trực tuyến, KHÔNG stream, KHÔNG tải nhạc, KHÔNG nhúng player online** dưới mọi hình thức.
  App chỉ lưu **metadata** (tên bài, album, nghệ sĩ, credits, ảnh bìa) và **nội dung do người dùng tự viết**.
- File nhạc của người dùng chỉ được **đọc để phân tích**, không copy đi đâu, không upload, không rời khỏi máy.
- **Dữ liệu người dùng là tối cao**: điểm số và review do người dùng tự chấm KHÔNG BAO GIỜ bị ghi đè bởi
  bất kỳ thao tác refresh metadata nào.
- **Không analytics, không tracker, không telemetry.** Không gửi dữ liệu người dùng đi đâu.
- **TUYỆT ĐỐI KHÔNG được giả lập/fake kết quả.** Mọi số liệu phổ tần phải đến từ dữ liệu PCM decode thật;
  mọi metadata phải đến từ response API thật. Nếu một endpoint không trả dữ liệu như mong đợi, hoặc một
  yêu cầu bất khả thi về mặt kỹ thuật trên Android, hãy báo cho tôi kèm bằng chứng thật và đề xuất phương
  án thay thế — không được tạo dữ liệu giả để UI trông như đang hoạt động.

---

## 1. STACK & RÀNG BUỘC KỸ THUẬT (bắt buộc tuân thủ)

- Native Android, **Kotlin + Jetpack Compose**, Material 3 (dynamic color, dark/light theme).
- minSdk 26, targetSdk 36, Gradle Kotlin DSL, JDK 17, version catalog (`libs.versions.toml`).
- Kiến trúc: MVVM + Clean-ish (`data` / `domain` / `ui`), Hilt (DI), Coroutines + Flow,
  Navigation Compose, Room (DB local), DataStore (settings), WorkManager (xử lý nền), Paging 3.
- Network: Retrofit + OkHttp + kotlinx.serialization. Ảnh: **Coil** (có disk cache riêng).
- **KHÔNG dùng `ffmpeg-kit`** — thư viện này đã bị retire (tác giả dừng từ 01/2025, repo đã archived).
  Thay vào đó dùng chiến lược decode 2 tầng:
  - **Tầng 1** (mặc định, nhẹ): `androidx.media3` decoders + `MediaExtractor`/`MediaCodec` cho
    FLAC, WAV, MP3, AAC/M4A, Ogg Vorbis, Opus.
  - **Tầng 2** (mở rộng): FFmpeg dạng prebuilt AAR còn được maintain (ví dụ
    `dotintent/videokit-ffmpeg-android`), hoặc tự build FFmpeg + JNI wrapper, cho ALAC, AIFF,
    APE (Monkey's Audio), WavPack (.wv), TTA, Musepack, WMA, DSD (.dsf/.dff).
  - Bọc cả hai tầng sau interface `AudioDecoder` để thay thế nhau được. Định dạng không decode được
    thì báo lỗi rõ ràng, KHÔNG crash.
- FFT: **JTransforms** (hoặc TarsosDSP) thuần Java; có thể thay bằng KissFFT qua JNI nếu cần tốc độ.
- Đọc tag/metadata file local: **jaudiotagger** (hoặc taglib qua JNI), cộng với thông tin codec thật
  lấy từ tầng decode (sample rate, bit depth, bitrate, số kênh).
- Truy cập file: **Storage Access Framework** (`ACTION_OPEN_DOCUMENT`, `OpenMultipleDocuments`,
  `OpenDocumentTree` để quét cả folder, `ACTION_CREATE_DOCUMENT` để xuất) + `takePersistableUriPermission`.
  KHÔNG dùng `READ_EXTERNAL_STORAGE` legacy, KHÔNG xin `MANAGE_EXTERNAL_STORAGE`.
- Room migration đàng hoàng, **KHÔNG `fallbackToDestructiveMigration`**.
- **Nếu một thư viện bạn định dùng đã bị deprecated/archived, hãy chọn thư viện thay thế còn sống và ghi
  rõ lý do trong README.** Chỉ khai báo dependency thật đang tồn tại, không bịa version.

---

## 2. MÔ HÌNH DỮ LIỆU (Room)

- `Artist(id, name, sortName?, imageUri?, mbid?, note?)`
- `Album(id, title, artistId, releaseYear?, coverUri?, coverThumbUri?, genreTags[],
   albumType[ALBUM|EP|SINGLE|COMPILATION], label?, catalogNumber?, barcode?, country?,
   listenedDate?, isFavorite, manualScoreOverride?, reviewText?,
   mbid?, releaseGroupMbid?, discogsReleaseId?, deezerId?, sourceProvider?, metadataFetchedAt?,
   createdAt, updatedAt)`
- `Track(id, albumId?, title, trackNumber?, discNumber?, durationMs?, isStandalone,
   stars?, reviewText?, isSkip, isHighlight, listenedDate?,
   recordingMbid?, workMbid?, isrc?, createdAt, updatedAt)`
- `Credit(id, albumId?, trackId?, personName, personMbid?, role, instrumentOrAttribute?,
   sourceProvider, sourceUrl?, sortOrder)`
- `AudioAnalysis(id, trackId?, albumId?, fileName, fileUriOrPath, fileHash, container, codec,
   sampleRate, bitDepth, bitrate, isVbr, channels, durationMs, encoderTag?, cutoffHz,
   verdict, confidence, reasonsJson, spectrogramPngPath?, analyzedAt)`
- `RemoteMetadataCache(key, provider, jsonBody, etag?, fetchedAt, expiresAt)`
- `SearchHistory(id, query, searchedAt)`
- Quan hệ: 1 `Album` — n `Track`. Bài lẻ là `Track` với `isStandalone = true`.
- **Rating**: 0.5 → 5.0 sao, **bước 0.5** (setting đổi được sang 0.25 hoặc 1.0).
- Room **FTS4** cho full-text search trên tên album, tên nghệ sĩ, tên bài, và nội dung review.
- Mọi ID chính dùng **UUID** để merge backup giữa nhiều thiết bị không xung đột.

---

## 3. TÍNH NĂNG 1 — RATE & REVIEW

### 3.1 Chấm điểm và tính trung bình
- Chấm sao + viết nhận xét cho **cả album** hoặc cho **từng bài trong album**.
- **Tự động tính điểm trung bình album** từ điểm các bài đã chấm, hiển thị **số thập phân 2 chữ số**
  (ví dụ `4.27★`), kèm tiến độ `9/12 bài đã chấm`.
  - Chỉ tính bài đã có điểm; bài chưa chấm bị **bỏ qua**, không tính là 0.
  - Setting chọn cách tính: **trung bình đơn giản** (mặc định) hoặc **trung bình có trọng số theo
    thời lượng bài**.
  - Cho phép **ghi đè điểm album thủ công** (`manualScoreOverride`); khi đó UI hiện cả hai, dạng
    `4.5★ (avg 4.27)`.
- Đánh dấu nhanh mỗi bài: **highlight** (bài đỉnh nhất) và **skip** (bài hay bỏ qua).

### 3.2 Ba cách đưa album/track vào thư viện
1. **Thủ công**: form nhập tên album, nghệ sĩ, năm, tracklist (thêm/xoá/sắp xếp kéo-thả, nút
   "thêm nhanh N track").
2. **Import từ tag file nhạc local**: chọn folder/file → đọc tag (ARTIST / ALBUM / TITLE /
   TRACKNUMBER / DISCNUMBER / YEAR / embedded cover art) → dựng sẵn album + tracklist để người
   dùng xác nhận trước khi lưu.
3. **Tra cứu online** — xem toàn bộ **mục 4**.

### 3.3 Màn hình
- **Tab Library**: album/single dạng grid hoặc list (đổi được), hiện cover + tên + điểm trung bình.
  - Sort: điểm cao→thấp, thấp→cao, mới thêm, tên A-Z, năm phát hành, ngày nghe.
  - Filter: theo nghệ sĩ, tag/genre, khoảng điểm, album type, chỉ favorite, chỉ album chưa chấm xong.
- **Màn chi tiết album**: cover lớn, thông tin phát hành, điểm trung bình nổi bật ở giữa, review album,
  và tracklist — mỗi dòng có sao (chạm để chấm nhanh), icon highlight/skip, ô review mở rộng, và
  **badge chất lượng audio** nếu track đã được phân tích ở mục 5.
- **Tab Stats**: tổng số album/bài đã chấm, điểm trung bình toàn thư viện, histogram phân bố sao,
  top 10 album điểm cao nhất, top nghệ sĩ nghe nhiều nhất, số album chấm theo từng tháng (chart),
  và "Wrapped" theo năm.
- **Chia sẻ ảnh**: xuất thẻ ảnh PNG đẹp (cover + điểm + review ngắn) để share ra ngoài.

---

## 4. TÍNH NĂNG 2 — TRA CỨU METADATA ONLINE, TÌM KIẾM & CREDITS

Thiết kế interface `MetadataProvider` chung; mỗi nguồn là một implementation, bật/tắt độc lập trong
Settings, và người dùng sắp xếp được thứ tự ưu tiên.

### 4.1 Nguồn dữ liệu (ưu tiên nền tảng mở)

**A. MusicBrainz — nguồn CHÍNH (bắt buộc, luôn bật)**
- Base `https://musicbrainz.org/ws/2/`, luôn kèm `fmt=json`. Dữ liệu là **CC0 / public domain**
  → an toàn nhất để cache offline lâu dài.
- Search: `GET /release?query=<lucene>&limit=25&offset=0`, và tương tự cho `/release-group`,
  `/recording`, `/artist`. Hỗ trợ cả cú pháp Lucene (`artist:"Ado" AND release:"Kyougen"`) và
  search thô cho người dùng phổ thông.
- Lookup release: `GET /release/{mbid}?inc=artists+artist-credits+labels+recordings+release-groups+media+genres+tags`
- Lookup credits: `GET /release/{mbid}?inc=recordings+recording-level-rels+work-level-rels+work-rels+artist-rels+label-rels+url-rels+artist-credits`
  - LƯU Ý THỰC TẾ: MusicBrainz giới hạn tổ hợp `inc` và response có thể rất lớn. Nếu request tổng
    thất bại hoặc thiếu dữ liệu, **fallback** sang lookup từng recording
    (`GET /recording/{mbid}?inc=artist-rels+work-rels+artist-credits`) và từng work
    (`GET /work/{mbid}?inc=artist-rels`) để lấy composer/lyricist.
  - Chỉ gọi credits **khi người dùng chủ động bấm vào**, không prefetch hàng loạt.
- **RATE LIMITING (BẮT BUỘC, không được bỏ):**
  - Tối đa **1 request/giây** trung bình. Implement **token bucket** (capacity 5, refill 1 token/giây)
    trong một OkHttp `Interceptor` dùng chung cho mọi call MusicBrainz.
  - `User-Agent` bắt buộc đúng format: `YounekoRate/1.0.0 ( https://github.com/<repo> )`.
    Để User-Agent mặc định của OkHttp sẽ bị block.
  - Xử lý HTTP **503** bằng exponential backoff + jitter (1s, 2s, 4s, 8s, tối đa 5 lần), kèm thông báo
    thân thiện: "Máy chủ MusicBrainz đang tải cao, đang thử lại…".
  - Tôn trọng `ETag` / `If-Modified-Since` để giảm tải.

**B. Cover Art Archive — ảnh bìa (luôn bật, đi kèm MusicBrainz)**
- `https://coverartarchive.org/release/{release-mbid}/` (JSON list), ảnh nhanh `/front-250`,
  `/front-500`, `/front-1200`. Fallback sang `release-group/{mbid}/front-500`.
- Tải về lưu **internal storage** (`covers/{albumId}.jpg`), hai size: thumb 250 + full 1200.
- Không có ảnh → placeholder mascot mèo, và cho người dùng tự chọn ảnh từ máy.

**C. Discogs — nguồn PHỤ chuyên credits (tùy chọn, mặc định TẮT)**
- Base `https://api.discogs.com/`. Rất mạnh về **credits chi tiết theo từng track**, thông tin
  pressing/label/catalog number; đặc biệt tốt với nhạc cũ, vinyl, và nhạc anime/game/underground.
- **Người dùng tự dán Personal Access Token** của họ vào Settings — KHÔNG hardcode key trong app.
  Không có token vẫn dùng được nhưng chậm hơn.
- Rate limit **60 request/phút khi có token, 25 request/phút khi không** → throttle riêng, và đọc
  header `X-Discogs-Ratelimit-Remaining` để tự điều tiết. `User-Agent` riêng bắt buộc.
- Endpoint dùng: `/database/search?q=&type=release`, `/releases/{id}`, `/masters/{id}`.
- Ghi rõ trong Settings: "Discogs là nguồn phụ, dữ liệu thuộc Discogs và tuân theo API Terms of Use
  của họ; app chỉ cache tạm để bạn xem offline."

**D. Last.fm — tags/genre + mô tả (tùy chọn, mặc định TẮT)**
- `album.getInfo`, `track.getInfo`, `artist.getInfo` để lấy community tags (gợi ý genre), playcount,
  wiki summary ngắn. Cần API key do người dùng tự nhập; không có thì ẩn tính năng.
- Khi hiện đoạn mô tả phải kèm nguồn + link về Last.fm.

**E. Deezer Public API — fallback nhanh (tùy chọn, mặc định TẮT)**
- `https://api.deezer.com/search?q=`, `/album/{id}`, `/track/{id}` — không cần key, tiện cho nhạc mới
  ra mà MusicBrainz chưa cập nhật, và làm fallback ảnh bìa.
- CHỈ lấy metadata + ảnh bìa. **TUYỆT ĐỐI KHÔNG dùng field `preview` (link 30s), không phát audio.**

**F. ListenBrainz Labs (tùy chọn, nâng cao)**
- `https://labs.api.listenbrainz.org/` cho metadata lookup theo tên (artist + recording → MBID), dùng
  khi search MusicBrainz thường không khớp — hữu ích cho fuzzy matching.

**KHÔNG DÙNG**
- Không dùng Spotify / Apple Music / YouTube Music API để build thư viện offline, vì ToS của họ hạn chế
  lưu trữ metadata lâu dài ngoài nền tảng — trái với thiết kế của app này.
- Không scrape HTML của bất kỳ site nào. Chỉ dùng API công khai chính thức.

### 4.2 Thanh tìm kiếm (Unified Search)
Đặt **search bar nổi bật ở đầu tab Library**, cộng thêm **màn hình Search riêng** mở từ FAB "+".

- Ô nhập debounce **400ms**, loading indicator dạng mèo, nút clear, và voice input qua
  `RecognizerIntent` của hệ thống.
- **Local-first**, kết quả chia hai khu vực rõ ràng:
  1. **"Trong thư viện của bạn"** — từ Room FTS (album, track, nghệ sĩ, nội dung review). Hiện tức thì,
     luôn ở trên, không cần mạng.
  2. **"Kết quả trên mạng"** — từ các provider đang bật, chỉ query khi có mạng.
- Chip filter dưới ô search: `Tất cả | Album | EP | Single | Bài hát | Nghệ sĩ`.
- Mỗi dòng kết quả online hiện: cover thumb, tên, nghệ sĩ, năm, loại, quốc gia/label (nếu có), số track,
  và **badge nguồn** (`MB` / `Discogs` / `Deezer`). Item đã có trong thư viện → badge "Đã có" + điểm hiện
  tại, bấm vào mở luôn bản local.
- Phân trang infinite scroll (`limit=25&offset=`) với skeleton loading.
- **Tìm kiếm nâng cao** (mở rộng được): field riêng cho nghệ sĩ, tên album, năm, label, catalog number,
  barcode → ghép thành query Lucene cho MusicBrainz.
- **Lịch sử tìm kiếm** (10 lần gần nhất, xoá được) + gợi ý "nghệ sĩ bạn hay chấm".
- Trạng thái rỗng phải hữu ích: không tìm thấy → nút to **"Không thấy? Tự thêm thủ công"**, chuyển sang
  form nhập tay với từ khoá đã điền sẵn.
- Offline: chỉ hiện kết quả local + banner "Đang offline — chỉ tìm trong thư viện. Bạn vẫn có thể thêm
  album thủ công."

### 4.3 Luồng "chọn kết quả → đưa vào thư viện"
1. Bấm một kết quả online → mở **màn preview** (chưa lưu vào DB).
2. Preview hiện: cover lớn, tên album, nghệ sĩ, năm, label, tracklist đầy đủ (số thứ tự, tên bài, thời
   lượng, disc number cho album nhiều đĩa), tổng thời lượng, và nút xem credits.
3. Nếu MusicBrainz trả nhiều release cùng một release-group (bản Nhật, deluxe, remaster…) → hiện bộ
   **"Chọn phiên bản"** với thông tin phân biệt: năm, quốc gia, số track, format (CD/Digital/Vinyl),
   label, catalog number. Mặc định gợi ý bản digital/CD có nhiều track nhất.
4. Nút **"Thêm vào thư viện & chấm điểm"** → lưu Album + toàn bộ Track vào Room, tải cover về máy, rồi
   mở ngay màn chấm điểm.
5. Sau import, người dùng **sửa được mọi field** (tên sai, thiếu track, gộp/tách…), và có nút "Làm mới
   metadata từ nguồn" — nhắc lại: **điểm và review KHÔNG BAO GIỜ bị ghi đè**.
6. **Dedupe**: kiểm tra trùng theo `mbid` trước, rồi fuzzy match theo (tên album normalize + nghệ sĩ +
   năm) với ngưỡng Levenshtein; nghi trùng thì hỏi "Có phải bạn muốn mở album đã có?" thay vì tạo bản
   ghi mới.
7. Sau khi import, album **hoạt động hoàn toàn offline** — không cần gọi lại mạng để xem hay chấm.
8. `RemoteMetadataCache` TTL mặc định 30 ngày; Settings cho xem dung lượng cache và xoá cache.

### 4.4 Màn hình Credits (lazy — bấm vào mới tải)
Ở màn chi tiết album và chi tiết track, thêm nút **"Xem credits"** (icon người + note nhạc). Chỉ fetch khi
người dùng bấm, có cache, và xem được offline sau lần đầu.

Nội dung nhóm theo vai trò:
- **Sáng tác**: composer, lyricist, writer, arranger, translator (từ `work-rels` của recording → `work`
  → `artist-rels`). Không có dữ liệu work-level thì ghi rõ "MusicBrainz chưa có dữ liệu tác quyền cho bài
  này" thay vì để trống bí ẩn.
- **Sản xuất**: producer, co-producer, executive producer, programming, additional production.
- **Kỹ thuật**: recording / mixing / mastering / assistant / vocal engineer, studio hoặc venue (từ
  `place-rels` nếu có).
- **Trình diễn**: từng nghệ sĩ kèm nhạc cụ cụ thể và loại giọng (lead / background / guest vocals), hiển
  thị đúng `attributes` MusicBrainz trả về, ví dụ "Nakata Yasutaka — synthesizer, programming".
- **Khác**: remixer, featured artist, sampled from, cover of (link tới bản gốc), design/artwork,
  photography.
- **Thông tin phát hành**: label, catalog number, barcode/UPC, ngày phát hành + quốc gia, packaging,
  release status (official/promo/bootleg), media format, ISRC từng track.
- **Liên kết ngoài**: từ `url-rels` — trang chủ nghệ sĩ, Wikipedia/Wikidata, VGMdb, Bandcamp… Mở bằng
  browser ngoài, KHÔNG nhúng player.

Trình bày:
- Danh sách theo nhóm, mỗi nhóm thu gọn được; tên người ở trên, vai trò dạng chip nhỏ ở dưới.
- Bấm vào tên người → **"tất cả bài trong thư viện của bạn có người này tham gia"** (query local, giúp
  người dùng phát hiện producer/nhạc sĩ yêu thích của mình).
- Mỗi mục có badge nguồn nhỏ (`MusicBrainz` / `Discogs`) + nút "Mở trang gốc".
- Bật cả MusicBrainz và Discogs → **merge** hai bộ credits, ưu tiên MusicBrainz, đánh dấu mục chỉ có ở
  Discogs, normalize tên + role trước khi so để không tạo dòng trùng.
- Nút **"Đóng góp cho MusicBrainz"** dẫn tới trang edit của release đó.
- Credits lưu vào bảng `Credit` để **xuất được ra file backup** cùng mọi dữ liệu khác.

### 4.5 Triển khai kỹ thuật tầng network
- Mỗi provider có `Interceptor` throttle riêng + `User-Agent` riêng + timeout riêng (connect 10s, read 20s).
- `MusicMetadataRepository` trả `Flow<Resource<T>>` với state Loading / Success / Error (message tiếng Việt
  dễ hiểu). Chiến lược **cache-then-network**.
- Xử lý đầy đủ: không mạng, timeout, 503 rate limit, 404, JSON thiếu field. **Mọi field online phải
  nullable — MusicBrainz rất thường thiếu field, app KHÔNG được crash vì thiếu dữ liệu.**
- Test thủ công tối thiểu: 1 album J-pop, 1 album phương Tây có nhiều bản remaster, 1 album cổ điển nhiều
  đĩa, 1 single, và 1 album underground mà MusicBrainz gần như không có dữ liệu (kiểm tra graceful
  degradation).

---

## 5. TÍNH NĂNG 3 — AUDIO QUALITY CHECKER (kiểu Spek)

### 5.1 Nhập file
- Người dùng tự chọn file qua SAF: 1 file, nhiều file, hoặc cả folder (quét đệ quy).
- Hỗ trợ càng nhiều định dạng càng tốt, tối thiểu: **FLAC, WAV, AIFF/AIF, ALAC (.m4a/.caf), APE,
  WavPack (.wv), TTA, DSD (.dsf/.dff), MP3, AAC/M4A, Ogg Vorbis, Opus, WMA, Musepack (.mpc), AMR, 3GP.**
- DSD: nêu rõ đây là định dạng 1-bit, convert sang PCM (ví dụ 24-bit/176.4 kHz) trước khi FFT, và ghi chú
  rằng ngưỡng đánh giá khác PCM.
- Hàng đợi xử lý bằng WorkManager: chạy nền, có progress, huỷ được, không block UI. Xử lý **streaming
  theo block, KHÔNG load cả file vào RAM** (file 24/192 rất lớn).

### 5.2 Thông tin kỹ thuật hiển thị
Container, **codec thật (không tin phần mở rộng file)**, sample rate, bit depth, bitrate trung bình +
VBR/CBR, số kênh, thời lượng, encoder tag nếu có (`LAME3.100`, `libFLAC`, `iTunes`, `Lavf`…), MD5
signature của FLAC (verify được thì báo "FLAC stream integrity OK"), kích thước file, và **tỉ lệ nén thực
tế** (bitrate thực / bitrate PCM lý thuyết).

### 5.3 Spectrogram
- Trục X = thời gian, trục Y = tần số, màu = biên độ (dB).
- FFT: cửa sổ **Hann**, size chọn được 1024/2048/4096/8192, overlap 50–75%, tính trên mono-mix và có tuỳ
  chọn xem riêng kênh L / R.
- Thang dB 0 → -120 dBFS (chọn được), palette kiểu Spek + thêm viridis, magma, greyscale.
- Trục tần số linear (mặc định, giống Spek) và log (tuỳ chọn); có thang màu legend bên phải, lưới tần số
  ở 5/10/15/16/19/20/22 kHz.
- Render vào Bitmap trên background thread, hiển thị bằng Compose Canvas; hỗ trợ **pinch-zoom, pan, tap
  để xem giá trị** (thời điểm, tần số, dB).
- Nút **lưu spectrogram ra PNG** và nút chia sẻ.
- Chế độ xem thứ hai: **average FFT spectrum** (đường phổ trung bình toàn bài) — dễ đọc điểm cutoff hơn.

### 5.4 Bộ heuristics phán đoán chất lượng
Tính các chỉ số sau rồi tổng hợp thành verdict + độ tin cậy + **danh sách lý do**:

1. **Lowpass cutoff detection**: với mỗi frame FFT, tìm tần số cao nhất mà năng lượng còn vượt sàn nhiễu
   (> -90 dBFS, hoặc > noise floor + 6 dB). Lấy **percentile 90** qua toàn bộ frame để ra `cutoffHz` ổn
   định, bỏ qua đoạn đầu/cuối im lặng.
2. **Đối chiếu bảng cutoff đặc trưng của codec lossy** (dấu hiệu tham khảo, sai số ±500 Hz):
   ~16 kHz → MP3 128k; ~18–19 kHz → MP3 192k / AAC 128k; ~19–20 kHz → MP3 V0/256k, AAC 256k;
   ~20–20.5 kHz → MP3 320k, Opus/Vorbis chất lượng cao; ~15 kHz → MP3 ≤112k hoặc AMR/WMA thấp.
   Nếu file khai là FLAC/WAV 44.1 kHz mà cutoff nằm sát một mốc trên **và rất phẳng, dứt khoát** (sườn dốc
   > 60 dB trong < 500 Hz) → nghi vấn cao là transcode từ lossy.
3. **Hi-Res giả**: file khai 96/192 kHz nhưng **không có năng lượng thực nào trên 22.05 kHz** (hoặc trên
   nửa sample rate gốc) → nghi upsample từ 44.1/48 kHz.
4. **24-bit giả**: phân tích các bit thấp — nếu 8 (hoặc 16) bit thấp nhất của toàn bộ sample đều bằng 0,
   hoặc dynamic range thực đo được ≈ 96 dB → file 24-bit được pad từ 16-bit.
5. **Spectral holes / block artifacts**: vùng năng lượng bị "khoét" đột ngột theo band, hoặc pattern lặp
   theo chu kỳ block (576/1024 sample) — dấu hiệu psychoacoustic encoder.
6. **Dấu vết joint stereo**: trên một tần số nào đó hai kênh L/R gần trùng khớp hoàn toàn (correlation ≈ 1,
   hoặc side channel ≈ 0) trong khi dưới đó vẫn khác nhau.
7. **Tag mismatch**: encoder tag chứa `LAME`, `Lavf`, `iTunes` trong file FLAC/WAV; hoặc metadata
   `SOURCE=MP3`.
8. **Clipping & DC offset**: đếm sample chạm 0 dBFS liên tiếp, đo DC offset, true peak, và **dynamic range
   (DR) ước lượng** — báo như thông tin tham khảo (loudness war), KHÔNG tính vào verdict.

### 5.5 Verdict
Trả một trong các nhãn sau, kèm **điểm tin cậy 0–100%** và **danh sách lý do đọc được bằng tiếng Việt**:
- `HI-RES XÁC THỰC` — bit depth ≥ 24 và sample rate ≥ 48 kHz **và** có nội dung thật trên 22.05 kHz.
- `LOSSLESS (CD QUALITY)` — 16-bit/44.1–48 kHz, cutoff tự nhiên gần Nyquist, không thấy dấu vết lossy.
- `LOSSLESS NHƯNG NGUỒN GIỚI HẠN` — container lossless, không thấy artifact lossy, nhưng dải tần bị giới
  hạn do bản thu/master gốc (analog cũ, nhạc thập niên 60–80).
- `NGHI VẤN — CÓ THỂ LÀ TRANSCODE` — có ít nhất 1 dấu hiệu mạnh ở 5.4.
- `HI-RES GIẢ / UPSAMPLED` — theo dấu hiệu (3) và/hoặc (4).
- `LOSSY (ĐÚNG NHƯ KHAI BÁO)` — file vốn là MP3/AAC/Opus…, chỉ báo bitrate & cutoff, không coi là "gian".
- `KHÔNG XÁC ĐỊNH` — file quá ngắn, quá nhiều đoạn im lặng, hoặc decode thất bại.

**BẮT BUỘC** có info card minh bạch, không được giấu:
> "Phân tích phổ chỉ mang tính tham khảo, không phải bằng chứng tuyệt đối. Một số bản thu gốc vốn đã bị
> giới hạn dải tần, và một số codec lossy hiện đại không cắt tần số rõ ràng. Hãy đọc kèm lý do chi tiết
> bên dưới."

Mỗi lý do phải nói rõ **vì sao nghi ngờ**, ví dụ: "Cutoff đo được 19.98 kHz với sườn cắt rất dốc
(74 dB / 300 Hz) — đặc trưng của MP3 320 kbps, trong khi file khai báo là FLAC 16-bit/44.1 kHz."

### 5.6 Lưu kết quả & liên kết với thư viện rating
- Lưu vào bảng `AudioAnalysis`.
- Cho phép **gắn kết quả phân tích vào Track/Album** → màn chi tiết album hiện badge chất lượng
  (Hi-Res / Lossless / Nghi vấn) trên từng track.
- **Tab Analyses**: danh sách kết quả, filter theo verdict, và **so sánh 2 file cạnh nhau** (2 spectrogram
  xếp trên/dưới) — rất hữu ích khi so bản FLAC với bản MP3 cùng bài.

---

## 6. XUẤT / NHẬP DỮ LIỆU (BACKUP & RESTORE)

- **Xuất full backup**: một file `.zip` (tên `YounekoRate_backup_yyyyMMdd_HHmm.zip`) gồm:
  - `data.json` — toàn bộ Room DB serialize bằng kotlinx.serialization, có `schemaVersion` ở đầu file,
    bao gồm cả `Credit` và `AudioAnalysis`.
  - `covers/` — ảnh bìa.
  - `spectrograms/` — PNG spectrogram đã lưu.
  - `manifest.json` — appVersion, schemaVersion, exportedAt, số lượng bản ghi từng bảng, checksum
    SHA-256 từng file.
- **Xuất nhẹ**: chỉ `data.json` (không kèm ảnh) cho ai muốn file nhỏ.
- **Xuất CSV** và **xuất Markdown** (danh sách review đẹp để đăng blog/Notion).
- Ghi file qua SAF (`ACTION_CREATE_DOCUMENT`) để lưu ra Download / Drive / USB, kèm nút "Share" để gửi
  sang máy tính.
- **Nhập/restore**: đọc zip/json, validate schema, hiện preview (sẽ thêm X album, Y track, Z bản phân
  tích), rồi chọn **Merge** (giữ bản ghi cũ, thêm mới, xử lý trùng theo UUID + hỏi khi xung đột) hoặc
  **Replace** (xoá sạch rồi nhập, xác nhận 2 lớp).
- Format `data.json` phải là **JSON thuần, dễ đọc, được document hoá trong README** để đọc/restore được
  cả trên máy tính. Kèm một **script Python nhỏ trong `tools/`** để đọc file backup và in ra bảng review.
- **Auto backup**: setting tự xuất backup định kỳ (hằng tuần) vào folder người dùng chọn, giữ N bản gần nhất.
- **Credential tuyệt đối không được đưa vào backup:** Discogs Personal Access Token, Last.fm API key và mọi credential khác chỉ được lưu trong DataStore, không được serialize vào `data.json`, `manifest.json` hay bất kỳ file ZIP nào. Màn hình Export phải hiển thị cảnh báo rõ ràng về điều này.

---

## 7. UI/UX & THƯƠNG HIỆU

- Tên app **Youneko Rate!**, concept mèo (neko) — mascot mèo dễ thương ở empty state, loading, và khi
  hoàn tất phân tích. Tông màu chính tím pastel + trắng kem, bo góc lớn, micro-animation nhẹ
  (Compose animation), haptic feedback khi chấm sao.
- Bottom navigation 4 tab: **Library / Rate (thêm nhanh) / Analyze / Stats**, cộng màn Settings.
- Đa ngôn ngữ: **tiếng Việt (mặc định) và English**, `strings.xml` đầy đủ, không hardcode text.
- Accessibility: content description, hỗ trợ font scale, contrast đủ chuẩn; hỗ trợ màn hình lớn/tablet.
- Onboarding 3 bước giải thích hai tính năng chính và cam kết "dữ liệu của bạn ở lại trên máy bạn".

---

## 8. ATTRIBUTION & PHÁP LÝ (bắt buộc có trong app)

Màn **Settings > About & Data Sources** ghi rõ:
- "Dữ liệu album, bài hát và credits được cung cấp bởi **MusicBrainz** (giấy phép CC0) và
  **Cover Art Archive**. Cảm ơn cộng đồng MetaBrainz."
- Tên các nguồn phụ đang bật, kèm link tới ToS của từng nguồn.
- "Youneko Rate! không lưu trữ, phát, hay phân phối nội dung âm thanh. Ứng dụng chỉ lưu thông tin metadata
  và nhận xét do bạn tự viết. File nhạc của bạn chỉ được đọc để phân tích, không rời khỏi máy."
- Toggle **"Chế độ hoàn toàn offline"** — tắt mọi network call; app vẫn dùng được 100% chức năng chấm điểm
  và phân tích âm thanh.
- Ghi rõ: không analytics, không tracker, không gửi dữ liệu người dùng đi đâu.

---

## 9. YÊU CẦU BÀN GIAO

- Source code đầy đủ, cấu trúc module rõ ràng, comment tiếng Anh, code idiomatic Kotlin.
- Unit test cho: tính điểm trung bình album (kể cả trọng số & trường hợp chưa chấm hết), thuật toán cutoff
  detection (dùng WAV sine wave sinh sẵn trong test), serialize/deserialize backup, parser JSON
  MusicBrainz release có credits (file JSON mẫu trong `test/resources`), logic merge credits 2 nguồn,
  logic dedupe album, và test throttle đảm bảo không vượt 1 req/s.
- `README.md`: hướng dẫn build, sơ đồ kiến trúc, giải thích thuật toán mục 5.4 **kèm giới hạn của nó**,
  document format file backup, danh sách định dạng hỗ trợ, danh sách nguồn metadata, và mục
  "Legal & Privacy".
- Script build APK debug + release, và hướng dẫn ký APK.

---

## 10. CÁCH LÀM VIỆC — ROADMAP THEO GIAI ĐOẠN

Làm tuần tự, **mỗi giai đoạn phải build chạy được và demo được rồi mới sang bước sau**, và báo cáo cho tôi
sau mỗi giai đoạn:

1. Khởi tạo project, theme, navigation, Room schema đầy đủ, DI. (App chạy được, các tab trống)
2. Tính năng Rate & Review: nhập thủ công, chấm sao, tính trung bình thập phân, màn chi tiết album,
   search/sort/filter local.
3. Import metadata từ tag file nhạc local.
4. Tầng network + token bucket throttle + cache + MusicBrainz search & release lookup + màn Search
   local-first.
5. Màn preview + chọn phiên bản release + import vào Room + tải cover từ Cover Art Archive + dedupe.
6. Màn Credits (MusicBrainz recording/work rels) + bảng `Credit` + xem theo tên người.
7. Provider phụ (Discogs, Last.fm, Deezer, ListenBrainz Labs) + merge credits + Settings quản lý nguồn,
   token, cache.
8. Audio Quality Checker phần 1: decode audio đa định dạng + FFT + vẽ spectrogram + thông tin kỹ thuật.
9. Audio Quality Checker phần 2: bộ heuristics + verdict + lý do + lưu kết quả + so sánh 2 file + gắn
   badge vào thư viện.
10. Export/import backup, CSV, Markdown, auto backup, script Python trong `tools/`.
11. Stats, share ảnh, onboarding, đa ngôn ngữ, polish, unit test, README, build APK và màn Open source licenses.
12. PoC và decode audio tầng 2 mở rộng bằng thư viện prebuilt/JNI còn maintain cho ALAC, AIFF, APE, WavPack, TTA, DSD, Musepack, WMA và các định dạng mở rộng khác. Trước khi code phải đo kích thước APK tăng thêm và test ít nhất một thiết bị arm64; ưu tiên bản LGPL và ghi rõ codec bị loại nếu ràng buộc GPL.

Nếu có điểm nào bất khả thi hoặc một thư viện không hoạt động như dự kiến, hãy báo cho tôi ngay kèm log/
response thật và đề xuất phương án thay thế, thay vì âm thầm bỏ qua hoặc fake dữ liệu.

---

## 11. QUY TRÌNH GITHUB & CI/CD (bắt buộc)

- Tạo repo `youneko-rate` (private) và push code lên qua GitHub connector.
- **Sau mỗi giai đoạn ở mục 10**: commit với message rõ ràng (`feat(phase-4): musicbrainz search`),
  cập nhật `PROGRESS.md`, rồi push. Coi repo là nguồn trạng thái chính thức của dự án — nếu phiên
  làm việc mới bắt đầu, hãy clone repo và đọc `SPEC.md` + `PROGRESS.md` trước khi làm gì khác.
- Tạo `.github/workflows/android-build.yml`:
  - Trigger: `push` lên `main`, `pull_request`, và `workflow_dispatch` (để mình bấm build tay).
  - `runs-on: ubuntu-latest`, `actions/checkout@v4`, `actions/setup-java@v4` (temurin, JDK 17),
    `gradle/actions/setup-gradle@v4` để cache Gradle.
  - Chạy `./gradlew assembleDebug` và `./gradlew testDebugUnitTest`.
  - `actions/upload-artifact@v4` để upload `app-debug.apk` và báo cáo test, retention 30 ngày.
  - Nếu cần NDK (khi tích hợp FFmpeg/JNI): dùng `android-actions/setup-android` và khai báo đúng
    phiên bản NDK trong `build.gradle.kts`, đừng phụ thuộc NDK cài tay trên máy local.
- Thêm workflow thứ hai `release.yml` (trigger theo tag `v*`): build `assembleRelease`,
  ký APK bằng keystore lưu trong **GitHub Secrets** (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`), rồi tạo GitHub Release kèm APK. Hướng dẫn mình cách tự tạo keystore
  và cách encode base64 để dán vào Secrets — **tuyệt đối không commit keystore hay mật khẩu vào repo.**
- Thêm `.gitignore` chuẩn Android, và `README` ghi rõ badge trạng thái build.

