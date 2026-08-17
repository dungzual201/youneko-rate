# Phase 1 build sources and verified versions

These are the external sources consulted before locking the Gradle catalog:

| Component | Version used | Source |
|---|---:|---|
| Android Gradle Plugin | 9.3.1 | https://developer.android.com/build/releases/agp-9-3-0-release-notes and Google Maven metadata at https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml |
| Gradle wrapper | 9.5.0 | AGP 9.3 compatibility notes state Gradle 9.5.0; distribution https://services.gradle.org/distributions/gradle-9.5.0-bin.zip |
| JDK | 17 | AGP compatibility notes: https://developer.android.com/build/releases/agp-9-3-0-release-notes |
| Kotlin / Compose compiler plugin | 2.4.10 | Kotlin Compose compiler migration guide: https://kotlinlang.org/docs/compose-compiler-migration-guide.html |
| KSP Gradle plugin | 2.3.11 | KSP releases: https://github.com/google/ksp/releases; KSP quickstart: https://kotlinlang.org/docs/ksp-quickstart.html |
| Compose BOM | 2026.08.00 | Compose BOM guide: https://developer.android.com/develop/ui/compose/bom |
| Compose library mapping | Compose UI 1.12.0, Material 3 1.4.0 in BOM 2026.08.00 | https://developer.android.com/develop/ui/compose/bom/bom-mapping |
| Room | 2.8.4 | Room release notes: https://developer.android.com/jetpack/androidx/releases/room |
| Hilt | 2.60.1 | Maven Central metadata: https://repo1.maven.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml |
| AndroidX foundation | core-ktx 1.19.0, activity-compose 1.13.0 | Google Maven metadata |
| AndroidX lifecycle/navigation | lifecycle 2.11.0, navigation-compose 2.9.8 | Google Maven metadata |
| DataStore/WorkManager/Paging | DataStore 1.2.1, WorkManager 2.11.2, Paging 3.5.1 | Google Maven metadata |
| KotlinX | serialization 1.11.0, coroutines 1.11.0 | Maven Central metadata |

The official AGP 9.3 notes report compatibility with API 37 and below, Gradle 9.5.0, and JDK 17. The project keeps `targetSdk = 36` as required by SPEC, but uses `compileSdk = 37` because the verified Compose 1.12.0 and core-ktx 1.19.0 artifacts require compile API 37. CI installs `platforms;android-37.1` and `build-tools;37.0.0`. The Compose BOM guide documents the 2026.08.00 BOM. The KSP release notes document the 2.3.10/2.3.11 line fixes for Kotlin 2.4 defaults and AGP 9 built-in Kotlin support.

CI run #6 PASS: https://github.com/dungzual201/youneko-rate/actions/runs/32049411776. Both `assembleDebug` and `testDebugUnitTest` succeeded on Gradle 9.5.0/JDK 17. The debug APK/test-report artifact is 19.6 MB with SHA-256 `bdb1fd3c841d2d527343f9c583d6121bcf57e271e4618116ab152b03acdf51b3`.

## Phase 2 additions

The phase 2 dependency additions were limited to verified AndroidX artifacts already aligned with the project stack: `androidx.lifecycle:lifecycle-runtime-compose:2.11.0` for `collectAsStateWithLifecycle`, `androidx.room:room-testing:2.8.4` for Room test support, `androidx.test.ext:junit:1.3.0`, and `androidx.test:runner:1.7.0` for instrumentation DAO tests. No reorderable UI library was added; Compose `detectDragGesturesAfterLongPress` is used instead. Local `compileDebugAndroidTestKotlin` passed.

The phase 2 score core is pure Kotlin and has no new runtime dependency. Room schema version 2 was exported under `app/schemas/`; the migration adds explicit cascade foreign keys without destructive fallback. Local verification ran `clean assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin` successfully.
