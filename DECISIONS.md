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
