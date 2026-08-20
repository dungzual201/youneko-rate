package com.youneko.rate.data.export

import android.os.Build
import com.youneko.rate.BuildConfig
import java.time.Instant
import kotlinx.serialization.Serializable

const val CURRENT_BACKUP_FORMAT_VERSION = 1
const val CURRENT_DATABASE_SCHEMA_VERSION = 19
const val BACKUP_MANIFEST = "manifest.json"
const val BACKUP_DATABASE_ENTRY = "database/youneko.db"
const val BACKUP_SETTINGS = "settings.json"
const val BACKUP_EXPORT_RATINGS = "export/ratings.csv"
const val BACKUP_EXPORT_CREDITS = "export/credits.json"
const val BACKUP_EXTENSION = "younekorate"

@Serializable
data class BackupManifest(
    val format: String = "younekorate",
    val formatVersion: Int = CURRENT_BACKUP_FORMAT_VERSION,
    val createdAt: String = Instant.now().toString(),
    val app: BackupAppInfo,
    val dbSchemaVersion: Int,
    val device: BackupDeviceInfo,
    val counts: BackupCounts,
    val includesCovers: Boolean,
    val sha256: String = "",
    val checksum: Map<String, String> = emptyMap(),
)

@Serializable
data class BackupAppInfo(val versionName: String, val versionCode: Int)

@Serializable
data class BackupDeviceInfo(val model: String, val sdkInt: Int)

@Serializable
data class BackupCounts(
    val artists: Int,
    val albums: Int,
    val tracks: Int,
    val ratings: Int,
    val manualCredits: Int,
    val credits: Int,
    val covers: Int,
    val reviews: Int = 0,
)

@Serializable
data class SafeSettingsSnapshot(
    val offlineOnly: Boolean = false,
    val ratingScale: String = "FIVE_STARS",
    val scoreMode: String = "SIMPLE",
    val gridView: Boolean = true,
    val dynamicColor: Boolean = false,
    val sortOrder: String = "NEWEST",
    val unfinishedOnly: Boolean = false,
    val showCreditSources: Boolean = false,
    val creditSourceOrder: String = "FILE_TAG,GENIUS,DISCOGS,MUSICBRAINZ,DEEZER,ITUNES",
    val activeCreditSources: String = "FILE_TAG,MUSICBRAINZ",
    val creditsMergeMode: Boolean = false,
)

@Serializable
data class BackupPreview(
    val manifest: BackupManifest,
    val current: BackupCounts,
    val checksumValid: Boolean,
)

enum class RestoreMode { REPLACE, MERGE }

data class BackupValidation(val ok: Boolean, val message: String? = null, val preview: BackupPreview? = null)

data class RestoreReport(
    val matchedTracks: Int,
    val totalTracks: Int,
    val missingTracks: Int,
    val mergedRows: Int,
    val insertedRows: Int,
    val conflicts: Int = 0,
) {
    val summary: String
        get() = "Đã nối $matchedTracks/$totalTracks bài với file trong máy. " +
            "$missingTracks bài chưa tìm thấy file; điểm đánh giá vẫn được giữ." +
            if (conflicts > 0) " Có $conflicts xung đột updatedAt đã xử lý theo bản mới hơn." else ""
}

fun currentBackupAppInfo() = BackupAppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
fun currentBackupDeviceInfo() = BackupDeviceInfo(Build.MODEL ?: "Thiết bị Android", Build.VERSION.SDK_INT)
