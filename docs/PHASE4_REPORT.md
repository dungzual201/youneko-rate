# Phase 3 hotfix và Phase 4 verification report

## 1. SAF tag-reader hotfix

The original failure came from using a temporary file with the suffix `.audio`. Jaudiotagger selects its reader from a `java.io.File` extension, so every SAF input was reported as `No Reader associated with this extension:audio`. The fixed reader obtains the real display name, extracts a supported extension, detects magic bytes when the name has no usable suffix, copies to `cacheDir/import_<UUID>.<ext>`, parses with `AudioFileIO.read(tempFile)`, and deletes the temp file in `finally`. If jaudiotagger fails, Android `MediaMetadataRetriever` reads the original content URI before the file is classified as skipped.

Failures show the real display name and a Vietnamese reason. Folder traversal is recursive and ignores non-audio files without reporting them as failures.

## 2. Manual format matrix

The JVM manual matrix used real one-second fixtures generated with ffmpeg and tagged with Unicode Vietnamese/Japanese names. Jaudiotagger results were checked using the same `FieldKey` mapping as production.

| Format/input | Manual result | Metadata observed |
|---|---|---|
| FLAC, `tên có dấu.flac` | PASS | `Bài có dấu` / `Nghệ sĩ thử` / `アルバム テスト`, 1000 ms |
| MP3 ID3v2.3, Japanese filename | PASS | `Track v23` / `Artist v23` / `MP3 v2.3`, 1000 ms |
| MP3 ID3v2.4, Japanese filename | PASS | `Track v24` / `Artist v24` / `MP3 v2.4`, 1000 ms |
| M4A/ALAC, Japanese filename | PASS | `ALAC Track` / `ALAC Artist` / `ALAC Album`, 1000 ms |
| WAV | PASS | `WAV Track` / `WAV Artist` / `WAV Album`, 1000 ms |
| OGG Vorbis | PASS | `Ogg Track` / `Ogg Artist` / `Ogg Album`, 1000 ms |
| Opus | jaudiotagger direct reader does not support `.opus` | Production path invokes `MediaMetadataRetriever` fallback on the original SAF URI; an Android device/emulator run is still required to verify the fallback result. |
| FLAC with no extension, Japanese filename | PASS after magic detection | Parsed as FLAC and returned the embedded tags. |

## 3. UI and i18n

The Import screen is now opened from the `Đánh giá` tab. The `Phân tích` tab contains only the cat icon and a clear Phase 8 Audio Quality Checker placeholder; no fake spectrum, metrics or verdict are generated. The default `values/strings.xml` is Vietnamese, `values-en/strings.xml` is English, `library_count` uses plurals, and Android lint reports no `HardcodedText` or `MissingTranslation` findings.

## 4. MusicBrainz Phase 4

The network layer uses Retrofit, kotlinx-serialization, OkHttp and a Hilt `NetworkModule`. It sets a meaningful User-Agent, 15-second connect/read/write timeouts, debug-only BASIC logging, token bucket capacity 5 with one-token-per-second refill, exponential retries for 503 up to five times, and `Retry-After` handling for 429. `RemoteMetadataCacheEntity` is read before network access and uses a 30-day TTL. The offline switch prevents all requests. Online search uses Paging 3 with 25-result offsets; local FTS results render before online results, and online cards carry an `MB` badge. Release preview is read-only; adding to the library remains Phase 5.

## 5. Verification

The following local checks passed when run separately with reduced Gradle worker count to avoid sandbox memory pressure:

```text
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2
./gradlew :app:lintDebug --no-daemon --max-workers=2
./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=2
```

The full CI workflow [Android Build #32097543485](https://github.com/dungzual201/youneko-rate/actions/runs/32097543485) passed `assembleDebug`, unit tests and artifact upload. Connected instrumentation was not run because no Android emulator/device was available in the sandbox.

## 6. Screenshot limitation

No fake screenshots are included. The sandbox did not expose an emulator or connected Android device (`emulator -list-avds` and `adb devices` returned no target), so screenshots of the real Import preview, Analyze placeholder and online search screen could not be captured without fabricating evidence. The APK artifact from CI is available for installation on a device; after installation, those three screens should be captured as the final manual acceptance step.

## References

[1]: https://musicbrainz.org/doc/MusicBrainz_API "MusicBrainz API"
[2]: https://musicbrainz.org/doc/MusicBrainz_API/Search "MusicBrainz API Search"
[3]: https://central.sonatype.com/artifact/com.jakewharton.retrofit/retrofit2-kotlinx-serialization-converter "Retrofit Kotlin serialization converter on Maven Central"
