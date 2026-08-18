# Youneko Rate! — Technical Decisions

Tài liệu này ghi lại các quyết định kỹ thuật của dự án, lý do, trạng thái và giai đoạn liên quan. Mọi thay đổi quan trọng phải được bổ sung sau mỗi giai đoạn.

| Mã | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| D-0001 | Chọn Native Android với Kotlin + Jetpack Compose, Material 3. | Đây là stack bắt buộc trong SPEC và phù hợp với yêu cầu decode audio native, FFT, SAF và xử lý nền Android. | Đã quyết định |
| D-0002 | Thiết kế offline-first; chỉ dùng mạng cho metadata và cho phép tắt toàn bộ network. | Điểm, review, phân tích âm thanh và dữ liệu người dùng phải hoạt động trên máy; không analytics, tracker hay telemetry. | Đã quyết định |
| D-0003 | Không dùng `ffmpeg-kit`. | SPEC xác định thư viện đã bị retire từ 01/2025 và repo đã archived; dùng thư viện đã ngừng duy trì là rủi ro bảo trì và tương thích. | Đã quyết định |
| D-0004 | Bọc giải mã sau interface `AudioDecoder`, triển khai hai tầng: Media3/MediaExtractor/MediaCodec trước, FFmpeg prebuilt AAR còn được duy trì hoặc FFmpeg tự build + JNI cho định dạng mở rộng. | Tầng mặc định nhẹ cho các định dạng phổ biến; tầng mở rộng xử lý các định dạng Media3 không hỗ trợ. Interface giúp thay thế và kiểm thử độc lập. | Đã quyết định về kiến trúc; thư viện tầng 2 cần xác minh trước khi triển khai |
| D-0005 | Dùng JTransforms hoặc TarsosDSP cho FFT thuần Java; chỉ cân nhắc KissFFT/JNI nếu benchmark thực tế cho thấy cần thiết. | Tránh phụ thuộc native sớm, giảm độ phức tạp và vẫn đáp ứng FFT streaming trên background thread. | Đã quyết định về hướng; thư viện cuối cùng cần benchmark |
| D-0006 | Dùng SAF thay cho quyền lưu trữ legacy. | Phù hợp Android hiện đại, tôn trọng quyền riêng tư và yêu cầu chọn file/folder bằng URI có persistable permission. | Đã quyết định |
| D-0007 | Dùng UUID cho khóa chính và migration Room tường minh; không dùng `fallbackToDestructiveMigration`. | UUID giúp merge backup giữa thiết bị không xung đột; migration phá hủy sẽ làm mất dữ liệu người dùng, trái nguyên tắc dữ liệu là tối cao. | Đã quyết định |
| D-0008 | MusicBrainz là provider chính, luôn bật; Cover Art Archive đi kèm; provider phụ mặc định tắt. | MusicBrainz dùng dữ liệu CC0/public domain và phù hợp cache offline; provider phụ có điều kiện token, quota hoặc Terms of Use riêng. | Đã quyết định |
| D-0009 | Credits chỉ fetch lazy khi người dùng chủ động bấm; dùng fallback recording/work khi lookup release tổng hợp thất bại hoặc thiếu dữ liệu. | Giảm tải MusicBrainz và xử lý thực tế response lớn/giới hạn tổ hợp `inc`. | Đã quyết định |
| D-0010 | Mọi metadata online nullable và mọi lỗi API phải chuyển thành lỗi thân thiện, không crash và không fake dữ liệu. | API có thể thiếu field, timeout, 404 hoặc 503; dữ liệu giả bị cấm tuyệt đối trong SPEC. | Đã quyết định |
| D-0011 | Rate-limit MusicBrainz bằng token bucket capacity 5, refill 1 token/giây; User-Agent riêng đúng format; retry 503 với exponential backoff + jitter. | Đây là yêu cầu bắt buộc để tôn trọng giới hạn dịch vụ và tránh bị block. | Đã quyết định |
| D-0012 | Chưa chốt thư viện FFmpeg tầng 2, tag parser và FFT cho đến khi xác minh artifact/version còn hoạt động và benchmark trên thiết bị yếu. | Không được bịa dependency/version; cần bằng chứng build/runtime thực tế trước khi khóa lựa chọn. | Chờ xác minh |
| D-0013 | Tách decode tầng 2 thành giai đoạn 12; giai đoạn 8 chỉ triển khai tầng 1 với Media3/MediaCodec cho FLAC, WAV, MP3, AAC/M4A, Ogg Vorbis và Opus. Trước phase 12 phải làm PoC thư viện prebuilt còn maintain, đo APK tăng thêm và test ít nhất một thiết bị arm64. | Giảm phạm vi/rủi ro giai đoạn 8, ưu tiên nhu cầu fake-lossless thực tế và chỉ chọn FFmpeg sau khi có bằng chứng build, kích thước và runtime. Chấp nhận APK lớn hơn nếu có số liệu thật. | Đã quyết định; áp dụng phase 8 và 12 |
| D-0014 | Debug và release đều build universal APK; chỉ cân nhắc ABI splits nếu APK vượt 150 MB, và khi đó vẫn phát hành thêm universal APK trong GitHub Release. | App được sideload và người dùng cần một file duy nhất, tránh chọn sai ABI. | Đã quyết định |
| D-0015 | Thứ tự ưu tiên provider phụ là Discogs → Deezer → Last.fm → ListenBrainz Labs; tất cả mặc định tắt. | Discogs ưu tiên credits cho nhạc Việt/anime/game; Deezer là fallback nhanh cho bản phát hành mới; các nguồn còn lại theo sau. | Đã quyết định |
| D-0016 | Credential được xử lý theo suy giảm mềm: Discogs không token vẫn bật với throttle 25 request/phút và banner gợi ý token; Last.fm chỉ hoạt động khi có API key, nếu thiếu thì ẩn/disable kèm giải thích và link hướng dẫn; Deezer, MusicBrainz và ListenBrainz không cần key. | Không bắt buộc người dùng nhập token/key, nhưng vẫn phản ánh đúng điều kiện thật của từng provider và không hiện lỗi kỹ thuật khó hiểu. | Đã quyết định |
| D-0017 | Mỗi job phân tích tối đa 200 file mặc định, setting nâng tối đa 1000; cảnh báo folder lớn và cho chia job. `AudioAnalysis` giữ vĩnh viễn; chỉ dọn PNG spectrogram khi vượt cap 300 MB theo PNG cũ nhất, luôn có nút dọn thủ công; fileHash đổi thì coi là file mới. | Giữ lịch sử dữ liệu người dùng nhưng kiểm soát dung lượng ảnh render; tránh mất kết quả text và tránh phân tích nhầm file đã thay đổi. | Đã quyết định |
| D-0018 | File không decode được ở cả hai tầng vẫn tạo `AudioAnalysis` với `verdict = KHÔNG XÁC ĐỊNH`, `confidence = 0`, reason chứa nguyên nhân thật và giữ tag đọc được nếu có. | Người dùng cần thấy lịch sử đã thử file nào; lỗi phải minh bạch, không để file biến mất và không tạo dữ liệu giả. | Đã quyết định |
| D-0019 | Backup giữ ZIP/JSON thuần, không mã hóa mặc định; Discogs Personal Access Token, Last.fm API key và mọi credential khác tuyệt đối không được xuất vào `data.json`, chỉ lưu trong DataStore; màn hình Export phải cảnh báo rõ. | JSON thuần dễ đọc/restore bằng công cụ thông thường, đồng thời credential không được rời khỏi vùng cài đặt riêng của app. ZIP có mật khẩu chỉ để dành về sau. | Đã quyết định |
| D-0020 | Theo dõi “License & third-party obligations”: jaudiotagger là LGPL và phải dynamic-link; PoC FFmpeg phase 12 ưu tiên bản LGPL, ghi rõ codec bị loại vì GPL nếu có; liệt kê license mọi dependency và làm màn Open source licenses ở phase 11; license public của repo sẽ chốt sau giữa GPL-3.0 và MIT. | Tránh vi phạm nghĩa vụ phân phối/điều kiện copyleft khi dùng thư viện audio; repo hiện private nên chưa chốt license public. | Đã quyết định; cần kiểm tra license theo dependency |

## Quy tắc ghi quyết định

Mỗi quyết định mới phải nêu rõ vấn đề, phương án được chọn, lý do, tác động và giai đoạn áp dụng. Nếu thay thư viện so với SPEC, phải ghi tên thư viện thay thế, version đã kiểm chứng, lý do thay và bằng chứng build/test hoặc log lỗi của phương án cũ.

## Ghi nhận triển khai giai đoạn 1

- **Android foundation:** Chọn Kotlin + Jetpack Compose Material 3, Hilt, Room, DataStore, Navigation Compose, WorkManager, Paging 3 và version catalog theo SPEC. Bốn route chính Library/Rate/Analyze/Stats cùng Settings đã có scaffold; tính năng nghiệp vụ chưa được triển khai trước phase 2.
- **SDK compatibility:** Giữ `minSdk = 26` và `targetSdk = 36` đúng SPEC, nhưng dùng `compileSdk = 37` vì các artifact đã xác minh `androidx.compose.ui:ui-android:1.12.0` và `androidx.core:core-ktx:1.19.0` yêu cầu compile API 37. CI cài `platforms;android-37.1` và `build-tools;37.0.0`.
- **Build memory:** Dùng Gradle heap 1536 MB, một worker và Kotlin compiler in-process để build được trong sandbox ít RAM; cấu hình này vẫn build PASS trên CI JDK 17.
- **Room schema:** Tạo schema nền tảng cho Artist, Album, Track, Credit, AudioAnalysis, RemoteMetadataCache, SearchHistory và FTS4; bật export schema và migration strategy không destructive, chưa thêm fallback destructive.
- **Dependency provenance:** Các version chính và nguồn xác minh được lưu trong `BUILD_SOURCES.md`; không thêm `ffmpeg-kit` hay version dependency chưa kiểm chứng.
- **CI evidence:** Run [Android Build #6](https://github.com/dungzual201/youneko-rate/actions/runs/32049411776) của commit `7675fba60de2bbfe73e2f3d84b746758f1629249` PASS cả `assembleDebug` và `testDebugUnitTest`; artifact debug APK/test reports đã upload, digest `sha256:bdb1fd3c841d2d527343f9c583d6121bcf57e271e4618116ab152b03acdf51b3`.

## Ghi nhận triển khai giai đoạn 2

| Mã | Quyết định | Lý do và tác động | Trạng thái |
|---|---|---|---|
| D-0021 | Đặt `CalculateAlbumScoreUseCase` thuần Kotlin làm nguồn sự thật duy nhất cho điểm album, trả `null` khi chưa có bài nào được chấm, bỏ qua track chưa chấm và làm tròn HALF_UP đến 2 chữ số. | Logic dễ kiểm thử độc lập với Android; hỗ trợ trung bình đơn giản, trọng số theo `durationMs`, fallback trọng số bằng nhau khi thiếu duration và `manualScoreOverride`. | Đã quyết định; unit tests phase 2 PASS |
| D-0022 | Dùng `Room` foreign keys với `onDelete = CASCADE` cho Album→Track/Credit/AudioAnalysis và migration tường minh từ schema 1 lên schema 2. | Xoá album không để lại dữ liệu mồ côi; migration bảo toàn dữ liệu cũ và không dùng `fallbackToDestructiveMigration`. | Đã quyết định; schema 2 export và compile PASS |
| D-0023 | Dùng Room FTS4 làm chỉ mục local cho tên album, nghệ sĩ, track và review; cập nhật chỉ mục trong cùng transaction khi tạo/sửa dữ liệu. | Search local-first có thể tìm cả review và không phụ thuộc network; DAO instrumentation test xác minh query FTS. | Đã quyết định; test source đã thêm |
| D-0024 | Chọn `detectDragGesturesAfterLongPress` cho reorder tracklist thay vì thêm thư viện reorderable bên ngoài. | Không tăng dependency surface, API có sẵn trong Compose và đáp ứng kéo-thả sau long-press; `trackNumber` được đánh lại khi save theo thứ tự hiện tại. | Đã quyết định; compile PASS |
| D-0025 | Lưu `scoreMode`, `gridView`, sort và hai filter chính vào DataStore; dùng `StateFlow`/`collectAsStateWithLifecycle` cho UI. | Trạng thái người dùng được giữ khi quay lại từ detail; mọi ghi DB chạy qua `Dispatchers.IO`, rating/review phản ứng theo Room Flow. | Đã quyết định; compile và local build PASS |
| D-0026 | Dùng `SavedStateHandle` cho draft album editor và debounce 800 ms cho review album/track. | Process death không làm mất nội dung form; review không cần nút Save và không block UI. | Đã quyết định; compile PASS |
| D-0027 | StarRatingBar dùng bước mặc định 0.5, tap/drag, long-press để clear, haptic và semantics; album detail hiển thị manual score cùng average. | Phân biệt rõ chưa chấm với 0.5 sao thấp nhất và tạo thao tác chấm nhanh ngay trên track row. | Đã quyết định; UI compile PASS |
| D-0028 | Dependency mới chỉ dùng lifecycle runtime Compose, Room testing và AndroidX test runner; không thêm thư viện reorderable hay thư viện đã deprecated. | Giảm rủi ro bảo trì và giữ dependency đã xác minh; DAO test đặt ở `androidTest` để chạy trên thiết bị/emulator, còn unit test score chạy trong `testDebugUnitTest`. | Đã quyết định; compile AndroidTest PASS |


## Bug log — Phase 2 hotfix

| Mã | Bug và nguyên nhân gốc | Cách sửa | Test bảo vệ |
|---|---|---|---|
| BUG-0001 | Khi tạo album, `AlbumEditorViewModel.save()` chạy coroutine trên `Dispatchers.IO` và trước hotfix gọi callback `onSaved(albumId)` từ `onSuccess` sau `repository.saveAlbum(...)`. Callback đi vào `navController.navigate(...)` trong `AppNavigation`, nhưng Navigation Compose yêu cầu cập nhật lifecycle/navigation state trên main thread; vì vậy phát sinh `IllegalStateException: Method setCurrentState must be called on the main thread`. | Bỏ callback navigation khỏi ViewModel. `AlbumEditorViewModel` phát `AlbumEditorEvent.OpenAlbum(albumId)` qua buffered `Channel` sau khi repository commit thành công. `AlbumEditorScreen` collect event trong `LaunchedEffect`, nơi xử lý UI/navigation trên main thread, rồi mới gọi `onSaved(event.albumId)`. | Unit test `AlbumNavigationHotfixTest.add_emits_openAlbum_event_after_repository_returns_id` dùng fake `AlbumRepository`, xác minh repository được gọi và event chứa đúng ID `created-id`. |
| BUG-0002 | Khi xoá album, `AlbumDetailViewModel.deleteAlbum()` chạy repository trên `Dispatchers.IO`; trước hotfix callback `onDeleted()` gọi `navController.popBackStack()` trực tiếp từ coroutine IO. Đồng thời Flow chi tiết có thể emit `null` sau khi entity bị xoá, nhưng UI không có trạng thái deleted rõ ràng. Kết quả là cùng lỗi main-thread `setCurrentState` và nguy cơ xử lý null không an toàn. | `AlbumRepository.observeAlbum()` trả `Flow<LibraryAlbum?>`. `AlbumDetailViewModel` dùng sealed `AlbumDetailUiState` (`Loading`, `Content`, `AlbumDeleted`) và phát `AlbumDetailEvent.ExitAlbum` một lần khi Flow chuyển từ content sang null. `AlbumDetailScreen` collect event trong `LaunchedEffect` và gọi `onBack()` trên main thread. | Unit test `AlbumNavigationHotfixTest.delete_emits_exit_event_when_observed_album_flow_becomes_null` xác minh Exit event và trạng thái `AlbumDeleted`. Instrumentation tests `RateDaoTest.repositorySaveQueriesAllTracksAndDeleteEmitsNull`, `albumInsertQueriesAllTracksAndDeleteEmitsNull` và `deletingAlbumCascadesTracks` bảo vệ transaction, query đủ track, cascade delete và Flow null. |

Các thay đổi hotfix không nuốt exception bằng `try/catch`, không đổi nullable tùy tiện ngoài Flow chi tiết vốn có thể hợp lệ khi entity bị xoá, không đồng bộ metadata mạng và không đưa credential vào dữ liệu backup. Phase 2 chỉ được chuyển sang `DONE` sau khi commit/push và CI xác nhận xanh; trước thời điểm đó trạng thái vẫn là `IN-PROGRESS`.


## Bug log — Favorite cascade delete và quyết định gỡ Favorite

| Mã | Mô tả và root cause thật | Phạm vi sửa | Trạng thái |
|---|---|---|---|
| BUG-0003 | `AlbumDao.upsert()` tại phiên bản cũ dùng `@Insert(onConflict = OnConflictStrategy.REPLACE)` (DAO cũ, dòng 28–29). `RateRepository.updateAlbum()` gọi `albumDao.upsert(album.copy(...))` tại phiên bản cũ, dòng 176–177. SQLite thực hiện `REPLACE` như DELETE rồi INSERT; vì `AlbumEntity` là parent có foreign key `Album→Track/Credit/AudioAnalysis` với `onDelete = CASCADE`, bấm Favorite đã xoá toàn bộ children trước khi chèn lại album. | `AlbumDao` và `TrackDao` hiện dùng `@Insert(ABORT)` cho insert mới và `@Update(ABORT)` cho cập nhật entity hiện có. `RateRepository` dùng `insert/insertAll` khi tạo, `update` khi sửa. Audit cũng sửa `ArtistDao`, vì Artist là parent của Album và Artist REPLACE cũng có thể cascade-xoá album. Các biến thể update album được kiểm thử gồm listenedDate, reviewText, manualScoreOverride, title/releaseYear và coverUri/coverThumbUri. | Đã sửa; regression test đã thêm |

Regression test `RateDaoTest.updateAlbumVariantsPreserveTracksCreditsAndAudioAnalysis` tạo album có 3 track, 1 Credit và 1 AudioAnalysis, gọi lần lượt mọi biến thể cập nhật và xác minh số lượng/nội dung track, Credit và AudioAnalysis không đổi. Test cũ trên code REPLACE sẽ quan sát children bị cascade xoá; test mới dùng `@Update(ABORT)` và giữ nguyên children.

Favorite được gỡ hoàn toàn theo yêu cầu sản phẩm vì không còn cần thiết và vì nút này là đường dẫn đã kích hoạt data-loss bug. `isFavorite` đã được loại khỏi `AlbumEntity`, `LibraryUiState`, `SettingsStore`, ViewModel, Compose screens, resources, SPEC, README và Phase 2 report. Database dùng migration Room tường minh `MIGRATION_2_3`: rebuild bảng `albums` không có cột `isFavorite`, copy toàn bộ cột dữ liệu còn lại, tạo lại index và đăng ký migration trong Hilt; không dùng `fallbackToDestructiveMigration`. Preference `library_favorite_only` không còn được đọc/ghi, dữ liệu cũ trong DataStore được bỏ qua an toàn.


## Quyết định Phase 3 — Local tag import và UI polish

| Mã | Quyết định | Lý do |
|---|---|---|
| D-0029 | Import file nhạc dùng `ACTION_OPEN_DOCUMENT`/`OpenMultipleDocuments` và `ACTION_OPEN_DOCUMENT_TREE`; app xin persistable read permission và chỉ đọc local. | Hỗ trợ một file, nhiều file và cả folder mà không cần quyền storage rộng; audio không upload, không stream và không decode ở Phase 3. |
| D-0030 | Dùng `net.jthink:jaudiotagger:3.0.1` để đọc ARTIST/ALBUM/TITLE/TRACKNUMBER/DISCNUMBER/YEAR/genre/duration/embedded cover; parser chạy trên IO và file SAF được copy vào cache tạm. | Artifact/version đã kiểm tra từ Maven Central; project chính thức công bố hỗ trợ MP3, MP4/M4A, Ogg, FLAC, WAV, AIF, DSF, WMA và LGPL. |
| D-0031 | Import chạy qua `CoroutineWorker`; worker đọc lại tags sau preview, báo progress và ghi Room qua `AlbumRepository` EntryPoint. | Tránh block UI, có thể cancel, và vẫn local-first/offline. |
| D-0032 | Group album theo artist + album + year; nhiều disc được sắp theo disc/track; không có ALBUM trở thành bài lẻ. | Khớp tag semantics và không gộp nhầm các album cùng tên khác năm/nghệ sĩ. |
| D-0033 | Dedupe dùng normalize Unicode/diacritics và khóa title+artist+year. Track trùng được update chỉ để bổ sung metadata thiếu; `stars`, `reviewText`, `isSkip`, `isHighlight` luôn được giữ. | Metadata refresh không được ghi đè dữ liệu người dùng; duplicate import phải idempotent. |
| D-0034 | Embedded cover được lưu vào `filesDir/covers`; cover người dùng chọn trong preview được ưu tiên; không lưu credential hay file audio vào backup/network. | URI SAF có thể hết quyền sau worker; app-owned cover path bền vững hơn và vẫn không đưa audio ra ngoài máy. |
| D-0035 | Palette mặc định là tím pastel/trắng kem; dynamic color mặc định tắt và chỉ bật khi người dùng chọn `Màu theo hệ thống` trong Settings. | Giữ nhận diện YounekoRate theo SPEC, nhưng vẫn cho phép tích hợp màu Android có chủ đích. |
| D-0036 | StarRatingBar hiển thị 5 sao với half-fill và số điểm cạnh thanh; highlight/skip có tooltip/semantics; placeholder lớn giảm còn 150dp và dùng icon mèo vector tạm. | Cải thiện đọc nhanh, accessibility và empty state; mascot asset chính thức để dành phase sau theo TODO. |

Nguồn dependency Phase 3 và license được lưu trong `BUILD_SOURCES_PHASE3.md`. Không có credential, token hay file âm thanh nào được thêm vào repository.


## Bug log — Phase 3 SAF tag reader

### BUG-0002 — Tất cả file import bị đưa vào Skipped files

**Triệu chứng:** sau khi chọn file hoặc thư mục, mọi audio đều bị bỏ qua với lỗi `No Reader associated with this extension:audio`.

**Root cause:** `LocalAudioTagReader` đã truyền/copy dữ liệu SAF vào file tạm có hậu tố `.audio`. Jaudiotagger chọn reader dựa trên phần mở rộng của `java.io.File`, vì vậy không nhận được FLAC/MP3/M4A/WAV/OGG và diễn giải extension là `audio`; content URI không phải là input hợp lệ cho `AudioFileIO.read`.

**Cách sửa:** lấy tên thật bằng `DocumentFile.name`/`OpenableColumns.DISPLAY_NAME`; tách extension cuối cùng; nếu thiếu hoặc lạ thì dò magic bytes (`fLaC`, ID3/MP3 sync, RIFF/WAVE, OggS/OpusHead, ftyp, FORM/AIFF); copy stream vào `cacheDir/import_<UUID>.<ext>`; gọi `AudioFileIO.read(tempFile)` và luôn xóa file trong `finally`. Nếu jaudiotagger thất bại, dùng `MediaMetadataRetriever` với chính content URI để lấy title/artist/album/duration; chỉ tạo failure sau khi cả hai cách thất bại. Failure chỉ hiển thị tên file thật và thông báo tiếng Việt, không lộ URI.

**Test bảo vệ:** `ImportModelsTest.displayNameExtensionHandlesUnicodeMultipleDotsNoExtensionAndNull`, `ImportModelsTest.magicBytesDetectSupportedAudioContainers`, cùng manual matrix thực tế cho FLAC, MP3 ID3v2.3/v2.4, M4A/ALAC, WAV, OGG Vorbis, Opus và file không extension. Jaudiotagger 3.0.1 đọc thành công mọi mẫu trừ Opus trực tiếp; Opus đi qua fallback Android `MediaMetadataRetriever` theo thiết kế.

## Quyết định Phase 4 — MusicBrainz

| Mã | Quyết định | Lý do |
|---|---|---|
| D-0037 | Dùng Retrofit + kotlinx-serialization converter, OkHttp và Hilt `NetworkModule`; timeout connect/read/write 15 giây; logging BASIC chỉ thêm khi `BuildConfig.DEBUG`. | Tách network stack khỏi UI, test được API/parser và tránh log request ở release. |
| D-0038 | User-Agent cố định là `YounekoRate/1.0.0 (youneko-rate@users.noreply.github.com)`. | MusicBrainz yêu cầu meaningful User-Agent; app không dùng API key. |
| D-0039 | Token bucket capacity 5, refill 1 token/giây trong OkHttp interceptor; retry 503 exponential tối đa 5 lần; 429 đọc `Retry-After` seconds hoặc HTTP-date. | Tuân thủ giới hạn MusicBrainz và không để UI tự điều phối request. |
| D-0040 | `RemoteMetadataCacheEntity` dùng key URL/params + provider, JSON body, ETag, fetchedAt/expiresAt; TTL 30 ngày và đọc cache trước mạng. Search history dùng DAO hiện có, giữ 10 kết quả gần nhất. | Offline-first, giảm request lặp và giữ dữ liệu remote không trộn vào rating local. |
| D-0041 | Search dùng endpoint release-group mặc định với `fmt=json`, `limit=25`, `offset`; model/API cũng hỗ trợ release, recording, artist và release lookup `inc=artist-credits+labels+recordings+release-groups+media`. | Khớp API chính thức và sẵn sàng mở rộng tab tìm kiếm mà không đổi transport layer. |
| D-0042 | UI search có chip `Trong máy`/`Trực tuyến`; FTS local render trước online Paging 3, online result có badge `MB`; preview release chỉ đọc và nút thêm thư viện chưa có. | Không phá local-first và giữ việc import release cho Phase 5. |

Nguồn API/dependency đã lưu trong `NETWORK_SOURCES_PHASE4.md`. Không commit API key hoặc credential.

## Bug log — folder import crash hotfix

### BUG-0003 — Crash khi import thư mục lớn ngay lúc enqueue

**Triệu chứng:** chưa có stack trace adb thực tế trong sandbox vì môi trường không có `adb`/emulator; code review xác định enqueue folder lớn có thể ném `IllegalStateException` do vượt giới hạn khoảng 10 KB của WorkManager `Data`, đồng thời preview giữ nhiều cover `ByteArray` có nguy cơ OOM.

**Cách xác minh:** không đoán stack trace. Đã ghi nhận minh bạch giới hạn thiết bị kiểm thử; thay thế bằng kiểm tra code và unit test kích thước payload. Worker hiện chỉ nhận `KEY_SESSION_ID`, session lưu source URI/selections trong Room, worker re-scan DocumentFile sau process death, quyền tree URI được persist, cover được ghi cache thành file path, track chèn theo batch 50 và lỗi từng file/group được cô lập.

**Quyết định:** không truyền mảng URI hoặc JSON selections trong WorkManager Data; không dùng `REPLACE` cho Album/Artist/Track; chỉ dùng IGNORE cho batch track import và giữ ABORT cho parent entities.

## Quyết định Phase 5 và i18n

| Mã | Quyết định | Lý do |
|---|---|---|
| D-0043 | `values/` là English fallback; `values-vi/` là Vietnamese; locale được đổi bằng `AppCompatDelegate.setApplicationLocales()` với System/English/Vietnamese. | Theo cơ chế AndroidX/AppCompat, không tạo DataStore key locale riêng và tránh ghép câu bằng code. |
| D-0044 | Search release-group mở preview release cụ thể; người dùng có thể chọn release trong release-group trước khi import. | Release-group có thể có nhiều bản phát hành khác nhau về country/date/tracklist. |
| D-0045 | Cover Art Archive thử `front-500`, fallback `front-250`, rồi vector cat; bytes được copy vào `filesDir/covers`, không lưu hotlink. | Offline-first và đáp ứng fallback cover art đã thống nhất. |
| D-0046 | Dedupe MusicBrainz theo release MBID, release-group MBID, rồi normalized title/artist/year; merge chỉ điền metadata còn thiếu, không ghi đè review/rating/cover user. | Bảo toàn dữ liệu người dùng và tránh album trùng. |

Nguồn Phase 5: `NETWORK_SOURCES_PHASE5.md`; không commit API key hoặc credential.

## Bug log — AppCompatActivity theme crash

### BUG-0004 — MainActivity crash tại `setContent`

**Stack trace thực tế:** `AppCompatDelegateImpl.createSubDecor` ném `You need to use a Theme.AppCompat theme (or descendant) with this activity` sau khi `MainActivity` được đổi từ Activity sang `AppCompatActivity` để dùng `AppCompatDelegate.setApplicationLocales()`.

**Root cause:** `Theme.YounekoRate` vẫn kế thừa platform theme `android:style/Theme.Material.Light.NoActionBar`, không phải AppCompat descendant. Vì vậy AppCompat không thể tạo sub-decor cho Activity, dù phần UI bên trong dùng Compose Material 3.

**Cách sửa:** đổi parent của theme sáng và night thành `Theme.Material3.DayNight.NoActionBar` từ Material Components; giữ `windowActionBar=false`, `windowNoTitle=true`, pastel status/navigation colors và thêm explicit `android:theme` cho cả application lẫn MainActivity. Thuộc tính navigation bar API 27 được đánh dấu `tools:targetApi="27"` vì minSdk là 26.

**Ràng buộc lâu dài:** việc dùng `AppCompatActivity` để backport per-app language xuống minSdk 26 kéo theo yêu cầu mọi Activity hiện tại và mọi Activity thêm sau này phải dùng theme là hậu duệ của `Theme.AppCompat`, luôn là biến thể `NoActionBar` khi UI được vẽ bằng Compose. Không được thêm Activity mới với `@android:style/...` hoặc theme không tương thích.

**Test hồi quy:** `MainActivityThemeLaunchTest` dùng Robolectric `ActivityScenario.launch(MainActivity::class.java)` cho cả cấu hình sáng và `night`; test được chạy trong `testDebugUnitTest` và có bước CI riêng.


## Quyết định Phase 6 — Credits và network crash hotfix

| Mã | Quyết định | Lý do và tác động | Trạng thái |
|---|---|---|---|
| D-0047 | Credits MusicBrainz chỉ lazy-load sau khi người dùng bấm `Xem credits`; album-level dùng release relations, track-level dùng recording rồi work relations; màn hình hiển thị progress X/Y và nút Hủy. | Không tự động tạo tải mạng khi mở album; coroutine có thể hủy giữa các recording; dữ liệu được ghi vào bảng Credit để hiển thị offline lần sau. | Đã quyết định; Phase 6 |
| D-0048 | Credits cache trong `RemoteMetadataCache` dùng key theo release/recording MBID và TTL 30 ngày; đọc cache trước khi gọi API, `forceRefresh` chỉ chạy từ nút tải lại. | Giảm request lặp, hỗ trợ offline-first và bảo đảm lần mở sau hiển thị ngay dữ liệu đã lưu. | Đã quyết định; test TTL PASS |
| D-0049 | `CreditMerger` chống trùng bằng normalize Unicode NFD bỏ dấu, lowercase, gom whitespace, bỏ hậu tố `(2)` và normalize role; mapping role gồm WRITING, PRODUCTION, ENGINEERING, PERFORMANCE, RELEASE, OTHER. | Một người có thể xuất hiện qua nhiều relation hoặc nhiều endpoint; kết quả không lặp theo biến thể tên/role, nhưng vẫn giữ MBID, instrument và source URL. | Đã quyết định; unit tests PASS |
| D-0050 | Không dùng `REPLACE` để cập nhật Album/Artist/Track; credits chỉ dùng upsert trên bảng con sau khi xóa đúng scope album/track. | SQLite `REPLACE` thực hiện DELETE + INSERT và có thể kích hoạt cascade xóa dữ liệu parent-child; rating/review/audio analysis của người dùng phải được bảo toàn. | Đã quyết định; kế thừa BUG-0003 |

## Bug log — Network permission và exception boundary

| Mã | Bug và nguyên nhân gốc | Cách sửa | Test bảo vệ |
|---|---|---|---|
| BUG-0005 | Crash thực tế là `SecurityException: Permission denied (missing INTERNET permission?)`; app gọi MusicBrainz nhưng manifest chưa khai báo quyền `android.permission.INTERNET` (và chưa khai báo network state). | Thêm `INTERNET` và `ACCESS_NETWORK_STATE` trực tiếp dưới `<manifest>`, ngoài `<application>`; không dùng `tools:node="remove"`. | `NetworkManifestTest` dùng Robolectric PackageManager kiểm tra cả hai permission; merged manifest report đã xác nhận quyền tồn tại. |
| BUG-0006 | Exception mạng từ Retrofit/OkHttp/Paging có thể thoát coroutine boundary và làm app crash, thay vì trở thành trạng thái lỗi có thể retry. | Thêm `Throwable.toNetworkError()`, bọc search/lookup/import, `PagingSource.load` luôn trả `LoadResult.Error`, Flow có `catch`, và `CoroutineExceptionHandler` chỉ làm lưới an toàn cuối. UI có resource riêng cho NO_CONNECTION, TIMEOUT, RATE_LIMITED, SERVER_ERROR, BAD_REQUEST, PARSE_ERROR, UNKNOWN và nút thử lại. | `MusicBrainzNetworkTest` kiểm tra 7 mapping, PagingSource trả `LoadResult.Error`; full unit test và lint PASS. |


## Bug log — Online search trả rỗng sau Phase 6

| Mã | Bug và nguyên nhân gốc | Cách sửa | Test bảo vệ |
|---|---|---|---|
| BUG-0007 | Search online có thể hiện empty dù API HTTP 200 vì response DTO chưa mô hình hóa đầy đủ các key gạch ngang (`label-info`, `catalog-number`, `track-count`, `release-events`, `text-representation`, aliases và các field type/primary IDs). Ngoài ra cache cũ có thể giữ response rỗng; repository lưu cache trước khi kiểm tra `count/items`, và query tự do chưa escape/percent-encode ký tự đặc biệt Lucene. | Bật `HttpLoggingInterceptor.Level.BODY` chỉ trong debug; cấu hình `Json` với `ignoreUnknownKeys`, `coerceInputValues`, `explicitNulls=false`, `isLenient=true`; bổ sung `@SerialName`/nullable defaults toàn bộ DTO; escape Lucene rồi UTF-8 percent-encode, Retrofit dùng `@Query(encoded=true)`; xóa cache cũ rỗng/invalid và chỉ cache response có item; Paging dừng theo top-level `count`; empty-state hiển thị chính keyword. | `MusicBrainzSearchHotfixTest` parse response thật release-group/release, parse thiếu field, mapper không rỗng, kiểm tra URL/query/UTF-8 escape; `MusicBrainzNetworkTest` kiểm tra không cache `count=0`; full assemble/unit test/lint PASS. |

### Quy tắc DTO MusicBrainz

Mọi key JSON có gạch ngang phải có `@SerialName` tương ứng trong DTO (`release-groups`, `release-group`, `artist-credit`, `first-release-date`, `primary-type`, `secondary-types`, `label-info`, `catalog-number`, `track-count`, `disc-count`, `release-events`, `text-representation`, `status-id`, `packaging-id`, `type-id`, `sort-name`, `iso-3166-1-codes` và các biến thể tương tự). DTO dùng `nullable + default` cho field có thể vắng; không dùng default rỗng để che lỗi response ở tầng repository.


## Bug log — Search/Import/Credits hotfix lần 8

| Mã | Bug và nguyên nhân gốc | Cách sửa | Test bảo vệ |
|---|---|---|---|
| BUG-0008 | Luồng Online trong `LibraryScreen` cùng tồn tại với nhánh EmptyLibrary/local FTS; khi thư viện trống, empty layout có thể chiếm viewport và làm người dùng tưởng online không chạy. Online query cũng cần được ràng buộc rõ ràng chỉ với text ô search. | Tách local và online thành hai nhánh UI độc lập; khi chip Online bật không render local FTS/EmptyLibrary; `MusicBrainzSearchViewModel` dùng pipeline query text riêng `debounce → trim → distinct → flatMapLatest`, dưới 2 ký tự trả `PagingData.empty()`. Panel online nhận `Modifier.weight(1f)` và hiển thị kết quả trong vùng riêng. | `MusicBrainzSearchHotfixTest`, `MusicBrainzNetworkTest` và full unit test; fixture response thật release-group/release chứng minh mapper không rỗng. |
| BUG-0009 | Loading import được render inline trong cuối màn hình/LazyColumn, khiến bottom navigation che mất indicator và chỉ còn một vệt màu khó nhận biết. | Import MusicBrainz dùng `MusicBrainzImportProgress` theo stage RELEASE/COVER/SAVING, dialog modal không dismiss bằng outside/back, có CircularProgressIndicator, progress X/Y và nút Hủy cancel coroutine. Local file import cũng chuyển WorkManager progress vào modal dialog; nút hủy gọi cancel work. Import thành công từ MB giữ callback mở album detail. | Kotlin/Compose compile PASS; full unit test/lint PASS. Manual dialog positioning vẫn cần xác minh trên thiết bị thật vì sandbox không có emulator/adb. |
| BUG-0010 | Credits trước đây có thể gọi sai SEARCH hoặc không parse được relation thật; relation credits theo bài nằm trong `media[].tracks[].recording.relations[]`, và relation JSON có `target-type`, `type-id`, `attribute-values`, `attribute-ids`, `attribute-credits`. Album/track không có MBID không được gọi network. | `lookupRelease/{mbid}` dùng includes artist-credits/labels/recordings/artist-rels/label-rels/recording-level-rels/work-rels/work-level-rels; recording fallback dùng `lookupRecording/{mbid}`, work dùng `lookupWork/{mbid}`. DTO đã thêm SerialName/map fields; service parse top-level và embedded recording relations, map engineer task mix/mastering, bỏ target URL khỏi credit. Credits dùng sealed states `Loading/Error/NoMbid/Empty/Data`; merge import cập nhật recordingMbid theo title không REPLACE parent. | Fixture thật `release_credits.json` của `cca05a87-b45b-4f90-9988-4c89998b1b2f`: SKINNY có 35 relations, CreditService tạo >=15 credits và assert Brad Lauchert/Mixing engineer, FINNEAS instruments, Andrew Yee/Amy Schroeder; endpoint MockWebServer test; NoMbid không gọi network; cache TTL PASS. |

### Quyết định bổ sung

| Mã | Quyết định | Lý do và tác động | Trạng thái |
|---|---|---|---|
| D-0051 | Online và local search là hai flow/UI độc lập; Online chỉ nhận text người dùng, không đọc DB và không phụ thuộc số album local. | Bảo đảm thư viện trống vẫn tìm MusicBrainz được và tránh EmptyLibrary che khuất kết quả. | Đã triển khai; test PASS |
| D-0052 | Import progress phải là modal dialog có nút Hủy; không đặt overlay progress ở cuối LazyColumn. | Tránh bottom navigation che indicator; stage và X/Y phải luôn nhìn thấy. | Đã triển khai; cần manual device verification |
| D-0053 | Credits luôn lookup theo MBID; release lookup là nguồn chính cho top-level và embedded recording relations, recording/work lookup là fallback; state NoMbid chặn mọi request. | SEARCH không nhận query MBID/rỗng đúng semantics; MBID guard bảo vệ offline-first và tránh 400. | Đã triển khai; fixture thật và endpoint tests PASS |


## Bug log — Cover Art Archive và UI A–C

> Các mã `BUG-0008` và `BUG-0009` đã được dùng cho Search/Import/Credits hotfix ở trên; để tránh trùng lịch sử, các bug mới dùng mã kế tiếp.

| Mã | Bug và nguyên nhân gốc | Cách sửa | Test/bằng chứng |
|---|---|---|---|
| BUG-0011 | Cover Art Archive dùng chung OkHttp với MusicBrainz nên ảnh phải đi qua token bucket 1 request/giây; một batch 25 ảnh có thể mất khoảng 25 giây và redirect HTTP 307 sang archive.org có thể không được theo đúng cấu hình client. | Tách `CoverArtApi` và `ImageLoader` sang OkHttpClient riêng, bật `followRedirects(true)` và `followSslRedirects(true)`, cache HTTP 20 MB, không gắn User-Agent/token bucket MusicBrainz. 404 được coi là `NotFound`, dùng placeholder mèo, không ném lỗi UI. | `CoverArtTest` xác minh URL fallback và mapping 404 → `NotFound`; compile/unit test/full verification PASS. Redirect và ảnh thật vẫn cần xác minh trên thiết bị/network thực tế. |
| BUG-0012 | Loading trong search/import có thể nằm inline ở cuối nội dung hoặc bị bottom navigation che, khiến indicator không được căn giữa vùng hiển thị. | Search refresh/preview/query-empty dùng vùng `weight(1f)` + `navigationBarsPadding()` và `contentAlignment = Alignment.Center`; append loader là item cao 56dp; import dùng modal dialog với CircularProgressIndicator và progress X/Y. | `assembleDebug`, `testDebugUnitTest` và `lintDebug` PASS; vị trí thực tế vẫn cần ảnh chụp thiết bị vì sandbox không có emulator/adb. |

### Quyết định bổ sung

| Mã | Quyết định | Lý do và tác động | Trạng thái |
|---|---|---|---|
| D-0054 | Dùng `release-group` làm định danh ưu tiên để tìm cover, sau đó fallback sang `release`; download ưu tiên `front-500` rồi `front-250`. | Release-group ổn định hơn giữa các bản phát hành/country khác nhau; thứ tự fallback giữ cover hiển thị ngay cả khi một release cụ thể thiếu ảnh. | Đã triển khai; URL builder test PASS |
| D-0055 | Ảnh cover tải về được promote vào `filesDir/covers/{albumId}.jpg`, ghi đường dẫn local vào Room và ưu tiên local-first; không hotlink online sau import. | Đáp ứng offline-first, tránh phụ thuộc URL từ xa khi xem lại album và giữ dữ liệu app-owned bền vững hơn URI SAF. | Đã triển khai; cần manual device verification |
| D-0056 | `ImageLoader` Coil là Hilt singleton; cấu hình crossfade, placeholder/error mèo, memory cache và disk cache đều bật. | Tránh tạo loader trong mỗi recompose, giảm request lặp và giữ fallback UI nhất quán toàn app. | Đã triển khai; compile/lint PASS |
| D-0057 | Track row chỉ hiển thị số thứ tự, tên, sao và menu ba chấm; review, credits, highlight, skip và preview nằm trong modal bottom sheet. Credits gộp cùng người theo MBID/tên, hiển thị số người theo nhóm, mở mặc định tối đa 3 nhóm và chỉ có footer nguồn MusicBrainz. | Giảm mật độ thông tin trên danh sách, tránh badge lặp trên từng dòng và làm rõ các thao tác phụ mà không làm mất chức năng. | Đã triển khai; compile/lint PASS; cần ảnh chụp thiết bị |


## Đính chính Credits và triển khai Phase 7–8 — 2026-08-18

| Mã | Quyết định | Lý do và tác động | Trạng thái |
|---|---|---|---|
| BUG-0013 | Credits thiếu dữ liệu không phải do MusicBrainz trả thiếu; parser chỉ đọc `release.relations[]` và bỏ qua `media[].tracks[].recording.relations[]`, cùng phần `performance → work → work.relations[]`. | Fixture thật release `42911e58-a29f-451b-91a4-38938ac19608` (AMORTAGE) cho thấy riêng “earthquake” có 14 recording relations và work có thêm 5 tác giả; parser cũ chỉ hiện hai dòng copyright. | Đã sửa; regression fixture/test đã thêm |
| D-0058 | Parser Credits phải đọc đồng thời release-level, label-info, track recording-level và work-level relations; `attribute-values`/`attribute-credits` được chuyển thành role/instrument semantics, `begin`/`end` được giữ trong Room. | Bảo toàn dữ liệu relation thật, hỗ trợ copyright year và gộp cùng người nhiều vai trò mà không lộ target URL trong dòng credit. Room migrations 4→5 giữ date, 5→6 giữ source URI/file name. | Đã triển khai; targeted/full tests PASS |
| D-0059 | Album Credits quan sát cả credit cấp album và credit cấp từng track; track Credits dùng route riêng với `trackId`; UI hiển thị bucket theo tên bài thay vì UUID. | Quan hệ recording/work thường thuộc từng bài; tách scope query tránh kết luận sai rằng album chỉ có copyright. | Đã triển khai; compile/unit test PASS |
| BUG-0014 | Track action “Play preview” tạo ra capability phát audio trái chỉ thị C1 và làm playback có thể quay lại qua UI/API. | Gỡ sạch preview action/string khỏi TrackRow, thay bằng “File information” chỉ đọc tên file và URI local; thêm `PlaybackCapabilityGuardTest` quét source/dependency để ngăn hồi quy. | Đã sửa; guard test PASS |
| D-0060 | Audio analysis là decode-only: SAF URI → `MediaExtractor`/`MediaCodec` → PCM trong memory; không tạo `MediaPlayer`, `AudioTrack`, ExoPlayer, Media3 hay audio output sink. | Đảm bảo app không có khả năng phát nhạc trong khi vẫn phân tích file local; foreground WorkManager giới hạn một job và hỗ trợ hủy. | Đã triển khai; cần test thiết bị thực tế |
| D-0061 | Phase 8 dùng JTransforms 3.1, FFT 4096, Hann window, hop 2048 và ba đoạn 30 giây quanh 25/50/75%; lưu cutoff, rolloff slope, crest-factor dynamic range, true peak, clipping, verdict, reasons và spectrum JSON. | Các chỉ số có thể kiểm thử thuần Kotlin và hiển thị offline; `AudioAnalysisEntity` được mở rộng qua Room migrations 6→7→8. Verdict chỉ là heuristic, luôn ghi confidence/reasons và cho phép `KHÔNG XÁC ĐỊNH`. | Đã triển khai; `AudioAnalysisTest` PASS; decoder vẫn cần matrix trên thiết bị |
| D-0062 | Discogs là provider phụ đầu tiên; mặc định tắt, token lưu DataStore, cache 30 ngày, client/rate limiter riêng 25 request/phút; offline mode chặn mọi provider. | Không chia sẻ MusicBrainz throttle/client; clear cache không xoá rating/review. Last.fm toggle/key đã có UI nhưng service Last.fm/Deezer/ListenBrainz chưa triển khai trong lượt này. | Discogs đã triển khai; provider phụ còn lại TODO |
| D-0063 | Không coi full verification local là hoàn tất sản phẩm khi chưa có CI và ảnh thiết bị thật. | Sandbox không có emulator/adb; cần người dùng cài APK, kiểm tra Credits/cover/artwork/Analyze và gửi ảnh trước khi tuyên bố hoàn tất. | Bắt buộc |

Full verification local ngày 2026-08-18: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` đều PASS. Cảnh báo strip native libraries của AndroidX/DataStore là non-fatal packaging warning; không có lint error.


## Bug log — FIX_ANALYZE_COVERS_CREDITS.md (A-D)

| Mã | Bug và nguyên nhân gốc | Cách sửa | Test/bằng chứng |
|---|---|---|---|
| BUG-0015 | FFT bin→Hz trước đây chia đôi tần số, nên cutoff thực tế khoảng 17.648–18.796 kHz bị báo thành 8.824–9.398 kHz và mọi verdict rơi vào `KHÔNG XÁC ĐỊNH`. | Chuẩn hóa công thức `binIndex * sampleRate / fftSize`; bin 2048 luôn là Nyquist `sampleRate/2`. Cutoff dùng noise floor median 5% bin cao nhất, stable 10 bins và percentile 95; slope hồi quy cục bộ ±40 bins, đơn vị dB/kHz; verdict/confidence được tính theo dữ liệu. | `AudioAnalysisTest` có regression test 1 kHz/15 kHz/48 kHz/Nyquist và sine sweep đến Nyquist; full `assembleDebug`, `testDebugUnitTest`, `lintDebug` PASS. |
| BUG-0016 | Parser credits gán recording/work relations vào `albumId`, làm track credits không xuất hiện đúng scope và dễ trộn với release-level credits. | Album-level giữ `albumId != null, trackId == null`; recording/work-level dùng `albumId == null, trackId != null`; cache đổi sang `credits:v2:`; refresh xóa đúng scope trước upsert. | Fixture AMORTAGE và `Phase6CreditsTest` xác nhận recording credits có `trackId`, `albumId == null`; release-level chỉ ở album scope; lookup work được kích hoạt. |

| Mã | Quyết định | Lý do và tác động | Trạng thái |
|---|---|---|---|
| D-0064 | Không dùng `covers.musichoarders.xyz`; cover art gọi trực tiếp iTunes Search, Deezer Album Search và Cover Art Archive, với embedded tag artwork là nguồn local ưu tiên. Ảnh hợp lệ phải có cạnh tối thiểu 500 px; ảnh được chọn theo cạnh lớn nhất và lưu JPEG quality 92 trong `filesDir/covers`. | Tránh phụ thuộc endpoint không có API công khai, giữ offline-first sau khi lưu local và tăng khả năng tìm cover cho album không có MBID. Deezer chỉ dùng `cover_xl`, tuyệt đối không dùng `preview`; iTunes nâng `100x100bb` lên `1200x1200bb`. | Đã triển khai trong commit `4f6ccfb`; CAA fallback và MockWebServer 404 regression vẫn PASS; cần xác minh provider/redirect trên thiết bị thật. |
| D-0065 | Credits từ embedded tag file là nguồn ưu tiên cao cho nhạc Việt và được đọc ngay khi import, không cần mạng. | jaudiotagger đọc COMPOSER, LYRICIST, PRODUCER, ARRANGER, PERFORMER, MIXER, ENGINEER, TIPL và TMCL; candidate lưu `sourceProvider = "file_tags"`, đi qua `ImportedTrack`/`TrackDraft` và được upsert theo `trackId` ngay trong import transaction. | Đã triển khai trong commit `1cb0ba7`; compile/KSP và full unit test PASS. |
| D-0066 | Credits thủ công (`sourceProvider = "manual"`) không bao giờ bị ghi đè hoặc mất khi refresh MusicBrainz hay nạp cache. | Trước khi xóa scope remote, đọc manual credits, merge chúng cùng candidates mới, sau đó upsert lại với scope gốc; manual source token được giữ khi provider được gộp. | Đã triển khai trong commit `e810ca1` và logic ở `MusicBrainzCreditsService`; full `assembleDebug`, `testDebugUnitTest`, `lintDebug` PASS. |
| D-0067 | Metadata cover cần ghi nhận nguồn và độ phân giải để UI/backup không mất provenance. | Thêm `coverSource` và `coverWidth` vào `AlbumEntity`, migration Room 8→9, schema artifact `9.json`, và cover picker grid từ các provider. | Room schema 9 sinh thành công; compile/KSP/full verification PASS. Manual device verification còn chờ ảnh người dùng. |
