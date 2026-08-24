<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/logo_paw.png">
    <img src="docs/images/logo_paw.png" width="140" alt="Youneko Rate paw logo">
  </picture>
  <h1>Youneko Rate!</h1>
  <p>Rate your local music library privately, clearly, and offline.</p>
  <p>
    <a href="README.vi.md">Đọc README tiếng Việt</a>
  </p>
  <p>
    <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/platform-Android-3DDC84.svg" alt="Platform Android"></a>
    <a href="app/build.gradle.kts"><img src="https://img.shields.io/badge/minSdk-26-blue.svg" alt="minSdk 26"></a>
    <a href="gradle/libs.versions.toml"><img src="https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg" alt="Kotlin 2.4.10"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-yellow.svg" alt="License MIT"></a>
    <img src="https://img.shields.io/badge/100%25%20Offline-6750A4?style=flat&logoColor=white" alt="100% Offline">
  </p>
</div>

## Contents

- [✨ Features](#-features)
- [📸 Screenshots](#-screenshots)
- [🛠️ Technology](#️-technology)
- [📋 Requirements](#-requirements)
- [🔨 Build](#-build)
- [🔒 Privacy](#-privacy)
- [⚠️ Limits](#️-limits)
- [🐾 Thanks](#-thanks)

Youneko Rate! is a personal, offline-first companion for keeping thoughtful album notes. It reads the local audio library, stores ratings and reviews on the device, and provides organization and inspection tools without pretending that unfinished beta work is complete.

<hr>

## ✨ Features

<img src="docs/images/cat_peek.png" width="90" align="right" alt="Chibi cat peeking">

The current app has four principal areas:

| Area | Current capability |
|---|---|
| **Library** | Scans local audio through MediaStore and optional SAF folders, then enriches local metadata and artwork. Scan progress is visible. |
| **Rate** | Supports manual track and album scores, reviews, tags, listening logs, and local rating filters. |
| **Analyze** | Uses decode-only inspection and STFT/FFT data to show sample rate, bit depth, bitrate and spectral indicators when the source provides enough data. |
| **Stats** | Summarizes saved ratings and analyses with distributions, rankings and a local share-card renderer. |

The interface is built around local data, Material 3, bilingual resources, a paw launcher/splash identity and artwork kept in the app's private storage.

<hr>

## 📸 Screenshots

The repository is still in beta and contains no real device screenshots. No screenshot was generated, fetched, or represented by a fabricated image. When captures become available, each image cell is reserved for a 250-pixel-wide image and a caption.

| Library | Album detail |
|---|---|
| **Library**<br>**THIẾU ẢNH:** `docs/images/screenshot_library.png`<br><sub>Real-device capture required</sub> | **Album detail**<br>**THIẾU ẢNH:** `docs/images/screenshot_album_detail.png`<br><sub>Real-device capture required</sub> |
| Rate | Analyze |
|---|---|
| **Rate**<br>**THIẾU ẢNH:** `docs/images/screenshot_rate.png`<br><sub>Real-device capture required</sub> | **Analyze**<br>**THIẾU ẢNH:** `docs/images/screenshot_analyze.png`<br><sub>Real-device capture required</sub> |
| Stats | — |
|---|---|
| **Stats**<br>**THIẾU ẢNH:** `docs/images/screenshot_stats.png`<br><sub>Real-device capture required</sub> | **THIẾU ẢNH:** second cell is intentionally unused |

Only one paw image is currently present. **THIẾU ẢNH:** `docs/images/logo_paw_light.png`, `docs/images/logo_paw_dark.png`, `docs/images/cat_peek_light.png`, `docs/images/cat_peek_dark.png`, `docs/images/cat_sit_light.png`, `docs/images/cat_sit_dark.png`. Until those paired assets exist, the `<picture>` fallback uses the existing `logo_paw.png` in both themes.

<hr>

## 🛠️ Technology

The versions below are read from `gradle/libs.versions.toml` and `app/build.gradle.kts`.

| Area | Actual implementation |
|---|---|
| Language and UI | Kotlin `2.4.10`, Jetpack Compose, Compose BOM `2026.08.00`, Material 3 |
| Android build | Android Gradle Plugin `9.3.1`, Gradle wrapper `9.5.0`, compile SDK `37`, min SDK `26`, target SDK `36` |
| Local data | Room `2.8.4` with KSP and exported schemas; DataStore Preferences `1.2.1` |
| Dependency injection | Hilt `2.60.1` and Hilt Navigation Compose `1.4.0` |
| Background work | WorkManager `2.11.2` for scans and other long-running local operations |
| Images and artwork | Coil `2.7.0`, AndroidX Palette `1.0.0`, AndroidX ExifInterface `1.3.7` |
| Audio and tags | MediaExtractor, MediaCodec, MediaMetadataRetriever, jaudiotagger `3.0.1`, JTransforms `3.1` |
| Platform support | core-ktx `1.19.0`, core-splashscreen `1.0.1`, Activity Compose `1.13.0`, Navigation Compose `2.9.8` |

The code uses a practical MVVM and repository/data-layer structure. Compose screens observe `StateFlow` from ViewModels; DAOs provide Room data, Hilt supplies dependencies, DataStore stores preferences and scan state, and WorkManager handles longer operations.

<hr>

## 📋 Requirements

| Setting | Value |
|---|---:|
| `minSdk` | 26 |
| `targetSdk` | 36 |
| `compileSdk` | 37 |
| Java/JDK | 17 |
| Kotlin | 2.4.10 |

<details>
<summary>Android permissions</summary>

| Permission | Why it is requested |
|---|---|
| `READ_MEDIA_AUDIO` on Android 13+ | Read local audio metadata from MediaStore. |
| `READ_EXTERNAL_STORAGE` on Android 12 and older | Read the equivalent local audio library on older Android versions. |
| `INTERNET` | Support explicitly requested online metadata actions already present in the beta. |
| `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` | Keep long-running local data work visible with a notification. |

The app does not request `MANAGE_EXTERNAL_STORAGE`.

</details>

<hr>

## 🔨 Build

<details open>
<summary>Build steps</summary>

```bash
git clone https://github.com/dungzual201/youneko-rate.git
cd youneko-rate
cp local.properties.example local.properties
# Set sdk.dir if Android Studio does not discover your SDK automatically.
./gradlew assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For the complete local verification suite:

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug compileDebugAndroidTestKotlin
```

</details>

<details>
<summary>Project structure</summary>

```text
app/src/main/java/com/youneko/rate/
├── data/          # Room entities/DAOs, repositories, import, scan, artwork and workers
├── di/            # Hilt and database dependency wiring
├── navigation/    # Compose navigation graph and routes
├── ui/
│   ├── analyze/   # Decode-only audio analysis and spectrogram UI
│   ├── artwork/   # Cover display, palette and cache-aware image loading
│   ├── components/ # Shared Material 3 components
│   ├── importer/  # Local audio import preview and save flow
│   ├── rate/      # Library, Rate, album detail, settings and editors
│   └── stats/     # Statistics and share-card rendering
└── res/           # Bilingual strings, themes, launcher/splash and vector assets
```

</details>

<hr>

## 🔒 Privacy

> **Privacy by design:** local ratings, reviews and library metadata stay on the device. The app requires no account, has no advertising or analytics, and does not require a server for local use.

Artwork imported into the app is stored under its private `filesDir/covers/` directory and is not written into the original audio file. A missing source file is marked `isMissing` instead of deleting its rating, review or manually entered data. Database migrations are explicit; the project does not use `fallbackToDestructiveMigration()`.

<hr>

## ⚠️ Limits

This is a beta project. The app has **no playback**, does not write original audio tags, does not retrieve lyrics, and does not provide backup. These boundaries are intentional and should not be read as promises of future functionality.

The repository has no real-device screenshots, no device-side performance measurements in this workspace, and no claim that the in-progress phases are complete. See `docs/PROGRESS.md` for the conservative phase ledger.

<hr>

## 🐾 Thanks

Thanks to the open-source Android ecosystem and to everyone testing this personal beta project. Behavior, local data flows and UI details may change as unfinished phases are evaluated.

<p align="center">
  <img src="docs/images/cat_sit.png" width="120" alt="Chibi cat sitting">
</p>

The project is released under the [MIT License](LICENSE).
