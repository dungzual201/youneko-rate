# FEAT_BRANDING_PALETTE_COVERSEARCH report

The feature series is based on Round 3 commit `9d59ded`. The ordered commit report is one line per feature commit:

1. `a5401fb` — `feat(ui)`: added the branded paw-and-name title to Library, Rate, Analyze, and Stats entry surfaces.
2. `cfdac2c` — `feat(palette)`: added Palette API extraction from a 128 px bitmap, Room `album_palette` cache, `coverUpdatedAt` invalidation, explicit migration, and migration test.
3. `6a18df7` — `feat(ui)`: added a completely static cover-derived detail gradient, true HSL lightness contrast adjustment, and removed the enlarged blurred cover background.
4. `b356a94` — `feat(cover)`: added fullscreen `ContentScale.Fit`, pinch zoom, pan, swipe dismissal, save/share controls, and a translucent fullscreen surface.
5. `8014d10` — `feat(palette)`: added the centered bottom palette strip with up to six 48 dp hit boxes and 44 dp circles.
6. `5167507` — `feat(ui)`: added `awaitFirstDown`/`waitForUpOrCancellation`, pressed-only hex tooltip, LongPress haptic, 800 ms clipboard copy, Confirm haptic, and Snackbar callback.
7. `528befa` — `feat(net)`: added the exact MusicHoarders POST endpoint, UTF-8 JSON body, line-by-line NDJSON Flow, cancellation, timeout client, and ten-minute in-memory cache with MockWebServer tests.
8. `4652245` — `feat(ui)`: added the full-screen cover-search form, source chips, country persistence, source status chips, adaptive result grid, grouped sorting, and fullscreen preview.
9. `c60303d` — `feat(cover)`: added streaming download validation, minimum 300×300 check, 20 MB limit, original staging file, 512 px JPEG, and Robolectric/JVM image tests.
10. `1373ec8` — `feat(cover)`: added Room cover metadata application, palette invalidation, Coil memory/disk invalidation, and a 30-second previous-cover undo snapshot.
11. `7d8cabb` — `feat(settings)`: documented and exposed batch-cover search as disabled; no unsafe WorkManager bulk requests are started (`CHƯA LÀM`).
12. `454b8f6` — `fix(i18n)`: completed English/Vietnamese cover-search, source, status, download, apply, and batch-state resources; key parity check returned no differences.
13. `final evidence commit` (hash is shown by the final `git log`) — `test(ui)`: palette contrast/order regression tests and this evidence report.

## Mandatory NDJSON evidence

`curl` was executed against `POST https://covers.musichoarders.xyz/api/search` with the requested Vietnamese artist/album, `country=us`, and selected sources. The server returned `HTTP 401` and an empty body (`0` lines), so there is no authentic server cover line to reproduce:

> `CHƯA LÀM: NDJSON evidence unavailable because the mandatory endpoint returned HTTP 401 in the sandbox; no cover line was received.`

The implementation and MockWebServer test still prove the required wire format and line-by-line parsing, but that local test fixture is not presented as real server evidence.

## Contrast evidence

The regression test computes these actual ratios from the implemented WCAG formula for three representative colors, not from three real album covers: `#101820` against white = **17.89:1**, `#F2C14E` against black = **12.51:1**, and `#6B2D5C` against white = **9.74:1**. Real album-cover contrast evidence is not available:

> `CHƯA LÀM: three real album cover colors could not be measured because no Android device/emulator or usable live cover response was available in this sandbox.`

## Screenshot and video evidence

> `CHƯA LÀM: real screenshots and the requested 10-second static-gradient / hold-release videos were not captured because this environment has no physical Android device, emulator, or ADB. No FPS or visual-device claim is made.`

The source-level checks preserve the stated invariants: no playback implementation was added, no lyrics crawling was added, no destructive Room fallback was added, no verdict/codec/cutoff/STFT/backup algorithm was changed, and downloaded cover art remains app-private rather than being written into user audio tags.
