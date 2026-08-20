

## Baseline audit — 2026-08-18

- Fixture thật đã tải từ MusicBrainz: `app/src/test/resources/fixtures/release_amortage.json` (43,178 bytes) và `work_earthquake.json` (2,433 bytes).
- Release `42911e58-a29f-451b-91a4-38938ac19608`, track `earthquake`, recording `fea3de6b-5913-4acd-be94-2067d2137b5d`: đúng 14 `recording.relations`; work `1477d866-1171-4cba-9ec7-1eaedb46afea`: đúng 5 writer relations.
- DTO hiện tại đã có `MbRelease.media[].tracks[].recording.relations`, `MbRelation.artist/label/work/url`, attributes và attribute-values; nhưng `MbRelation` chưa có `begin`/`end` nên copyright year chưa thể giữ trong `CreditEntity` hiện tại.
- `MusicBrainzCreditsService` hiện đã có nhánh đọc recording relations khi `recordingTracks` khớp `track.recordingMbid`, nhưng cần fixture regression để xác minh đường release media → recording, embedded work và fallback `/work`; attribute labels hiện chưa được chuyển thành localized semantic roles như “Mix”, “Trợ lý mix”, “Hát bè”.
- `CreditDao.observeForItem(albumId, trackId)` đã scoped đúng theo album/track; track credits không bị loại chỉ vì `trackId` khác null.
- Navigation hiện dùng một route chung `credits/{albumId}?trackId=...`; chưa có route riêng `credits/album/{albumId}` và `credits/track/{trackId}`. TrackRow bottom sheet có 5 mục nhưng mục thứ 5 là `Play preview`, cần thay bằng “Xem thông tin file” và xử lý close-sheet-before-navigate.
- `app/build.gradle.kts` và version catalog không chứa Media3/ExoPlayer/MediaPlayer/MediaSession/AudioTrack dependency; playback removal chủ yếu là UI/source guard. Giữ MediaExtractor/MediaCodec chỉ cho Phase 8, không đưa PCM ra AudioTrack.
- `CoverArtImage` hiện dùng `ContentScale.Crop` và AlbumDetail khung cao cố định 220dp; C3 yêu cầu vuông 1:1, `ContentScale.Fit`, tải 1200 → 500 → 250 và lưu JPEG quality 92.


## Session follow-up — parser fix, provider controls, Phase 8 v1

- Release fixture: `42911e58-a29f-451b-91a4-38938ac19608` (AMORTAGE); earthquake has 14 recording relations in `media[].tracks[].recording.relations[]`, plus five authors in work `1477d866-1171-4cba-9ec7-1eaedb46afea`.
- Credits parser now reads release-level, embedded recording-level, performance→work and work-level relations; maps `attribute-values`/`attribute-credits`, preserves begin/end dates and stores work MBID.
- Credits UI reads album + track scopes, displays track title buckets and semantic roles; TrackRow no longer has any play-preview action.
- Added `PlaybackCapabilityGuardTest`; source/dependency scan finds no MediaPlayer, ExoPlayer, Media3, MediaSession, AudioTrack, previewUrl or audio preview endpoint.
- Discogs provider is implemented with default-off toggle, DataStore token, 30-day cache, separate 25 req/min bucket/client, source chips and clear metadata cache. Last.fm toggle/key UI is present; Last.fm/Deezer/ListenBrainz services remain TODO.
- Phase8 v1 uses `MediaExtractor` + `MediaCodec` decode-only, three 30-second windows, FFT 4096 Hann/hop 2048, cutoff/rolloff/crest factor/true peak/clipping, heuristic verdict and foreground WorkManager. Analyze tab includes SAF picker, progress/cancel, local result card and FFT Canvas.
- Room schema is version 8 with migrations 4→5 (credit dates), 5→6 (track local source), 6→7 (audio metrics), 7→8 (spectrum JSON).
- Local full verification on 2026-08-18: `assembleDebug`, `testDebugUnitTest`, `lintDebug` PASS. No emulator/adb; no device screenshots or codec matrix evidence.
