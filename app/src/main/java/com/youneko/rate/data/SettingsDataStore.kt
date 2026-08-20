package com.youneko.rate.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "youneko_settings")

interface SettingsStore {
    val offlineOnly: Flow<Boolean>
    val ratingStep: Flow<Double>
    val ratingScale: Flow<String>
    val scoreMode: Flow<String>
    val gridView: Flow<Boolean>
    val dynamicColor: Flow<Boolean>
    val sortOrder: Flow<String>
    val unfinishedOnly: Flow<Boolean>
    val discogsEnabled: Flow<Boolean>
    val discogsToken: Flow<String>
    val lastFmEnabled: Flow<Boolean>
    val lastFmApiKey: Flow<String>
    val geniusEnabled: Flow<Boolean>
    val geniusToken: Flow<String>
    val showCreditSources: Flow<Boolean>
    val creditSourceOrder: Flow<String>
    val activeCreditSources: Flow<String>
    val creditsMergeMode: Flow<Boolean>

    suspend fun setOfflineOnly(value: Boolean)
    suspend fun setRatingStep(value: Double)
    suspend fun setRatingScale(value: String)
    suspend fun setScoreMode(value: String)
    suspend fun setGridView(value: Boolean)
    suspend fun setDynamicColor(value: Boolean)
    suspend fun setSortOrder(value: String)
    suspend fun setUnfinishedOnly(value: Boolean)
    suspend fun setDiscogsEnabled(value: Boolean)
    suspend fun setDiscogsToken(value: String)
    suspend fun setLastFmEnabled(value: Boolean)
    suspend fun setLastFmApiKey(value: String)
    suspend fun setGeniusEnabled(value: Boolean)
    suspend fun setGeniusToken(value: String)
    suspend fun setShowCreditSources(value: Boolean)
    suspend fun setCreditSourceOrder(value: String)
    suspend fun setActiveCreditSources(value: String)
    suspend fun setCreditsMergeMode(value: Boolean)
}

data class MediaScanCheckpoint(
    val lastScanTimeMs: Long = 0L,
    val lastGeneration: Long = -1L,
    val providerVersion: String = "media-scan-v1",
)

class MediaScanStore(private val context: Context) {
    private object Keys {
        val lastScanTimeMs = longPreferencesKey("media_scan_last_time_ms")
        val lastGeneration = longPreferencesKey("media_scan_last_generation")
        val providerVersion = stringPreferencesKey("media_scan_provider_version")
    }

    val checkpoint: Flow<MediaScanCheckpoint> = context.settingsDataStore.data.map {
        MediaScanCheckpoint(
            lastScanTimeMs = it[Keys.lastScanTimeMs] ?: 0L,
            lastGeneration = it[Keys.lastGeneration] ?: -1L,
            providerVersion = it[Keys.providerVersion] ?: "media-scan-v1",
        )
    }

    suspend fun save(lastScanTimeMs: Long, lastGeneration: Long, providerVersion: String) {
        context.settingsDataStore.edit {
            it[Keys.lastScanTimeMs] = lastScanTimeMs
            it[Keys.lastGeneration] = lastGeneration
            it[Keys.providerVersion] = providerVersion
        }
    }

    suspend fun reset() {
        context.settingsDataStore.edit {
            it[Keys.lastScanTimeMs] = 0L
            it[Keys.lastGeneration] = -1L
        }
    }
}

class PendingRestoreStore(private val context: Context) {
    private object Keys {
        val pending = booleanPreferencesKey("pending_restore")
        val stagingPath = stringPreferencesKey("pending_restore_staging_path")
        val preRestorePath = stringPreferencesKey("pending_restore_pre_restore_path")
    }

    suspend fun setPending(stagingPath: String, preRestorePath: String) {
        context.settingsDataStore.edit {
            it[Keys.pending] = true
            it[Keys.stagingPath] = stagingPath
            it[Keys.preRestorePath] = preRestorePath
        }
    }

    suspend fun state(): Triple<Boolean, String?, String?> = context.settingsDataStore.data.map {
        Triple(it[Keys.pending] ?: false, it[Keys.stagingPath], it[Keys.preRestorePath])
    }.first()

    suspend fun clear() {
        context.settingsDataStore.edit {
            it.remove(Keys.pending)
            it.remove(Keys.stagingPath)
            it.remove(Keys.preRestorePath)
        }
    }
}

class AutoBackupStore(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("auto_backup_enabled")
        val treeUri = stringPreferencesKey("auto_backup_tree_uri")
        val lastBackupAt = longPreferencesKey("last_backup_at")
    }

    val enabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.enabled] ?: false }
        val treeUri: Flow<String?> = context.settingsDataStore.data.map { it[Keys.treeUri] }
    val lastBackupAt: Flow<Long?> = context.settingsDataStore.data.map { it[Keys.lastBackupAt] }
    suspend fun setFolder(uri: String) { context.settingsDataStore.edit { it[Keys.treeUri] = uri } }
    suspend fun setEnabled(value: Boolean) { context.settingsDataStore.edit { it[Keys.enabled] = value } }
    suspend fun setLastBackupAt(value: Long) { context.settingsDataStore.edit { it[Keys.lastBackupAt] = value } }

}

class SettingsDataStore(private val context: Context) : SettingsStore {
    val themeMode: Flow<String> = context.settingsDataStore.data.map { it[Keys.themeMode] ?: "SYSTEM" }

    suspend fun setThemeMode(value: String) { context.settingsDataStore.edit { it[Keys.themeMode] = value } }

    private object Keys {
        val offlineOnly = booleanPreferencesKey("offline_only")
        val ratingStep = doublePreferencesKey("rating_step")
        val ratingScale = stringPreferencesKey("rating_scale")
        val scoreMode = stringPreferencesKey("score_mode")
        val gridView = booleanPreferencesKey("library_grid_view")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val themeMode = stringPreferencesKey("theme_mode")
        val sortOrder = stringPreferencesKey("library_sort_order")
        val unfinishedOnly = booleanPreferencesKey("library_unfinished_only")
        val discogsEnabled = booleanPreferencesKey("provider_discogs_enabled")
        val discogsToken = stringPreferencesKey("provider_discogs_token")
        val lastFmEnabled = booleanPreferencesKey("provider_lastfm_enabled")
        val lastFmApiKey = stringPreferencesKey("provider_lastfm_api_key")
        val geniusEnabled = booleanPreferencesKey("provider_genius_enabled")
        val geniusToken = stringPreferencesKey("provider_genius_token")
        val showCreditSources = booleanPreferencesKey("credits_show_sources")
        val creditSourceOrder = stringPreferencesKey("credits_source_order")
        val activeCreditSources = stringPreferencesKey("credits_active_sources")
        val creditsMergeMode = booleanPreferencesKey("credits_merge_mode")
    }

    override val offlineOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.offlineOnly] ?: false }
    override val ratingStep: Flow<Double> = context.settingsDataStore.data.map { it[Keys.ratingStep] ?: 0.5 }
    override val ratingScale: Flow<String> = context.settingsDataStore.data.map { it[Keys.ratingScale] ?: "FIVE_STARS" }
    override val scoreMode: Flow<String> = context.settingsDataStore.data.map { it[Keys.scoreMode] ?: "SIMPLE" }
    override val gridView: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.gridView] ?: true }
    override val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.dynamicColor] ?: false }
    override val sortOrder: Flow<String> = context.settingsDataStore.data.map { it[Keys.sortOrder] ?: "NEWEST" }
    override val unfinishedOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.unfinishedOnly] ?: false }
    override val discogsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.discogsEnabled] ?: false }
    override val discogsToken: Flow<String> = context.settingsDataStore.data.map { TokenCipher.decrypt(it[Keys.discogsToken].orEmpty()) }
    override val lastFmEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.lastFmEnabled] ?: false }
    override val lastFmApiKey: Flow<String> = context.settingsDataStore.data.map { TokenCipher.decrypt(it[Keys.lastFmApiKey].orEmpty()) }
    override val geniusEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.geniusEnabled] ?: false }
    override val geniusToken: Flow<String> = context.settingsDataStore.data.map { TokenCipher.decrypt(it[Keys.geniusToken].orEmpty()) }
    override val showCreditSources: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.showCreditSources] ?: false }
    override val creditSourceOrder: Flow<String> = context.settingsDataStore.data.map { it[Keys.creditSourceOrder] ?: "FILE_TAG,GENIUS,DISCOGS,MUSICBRAINZ,DEEZER,ITUNES" }
    override val activeCreditSources: Flow<String> = context.settingsDataStore.data.map { it[Keys.activeCreditSources] ?: "FILE_TAG,MUSICBRAINZ" }
    override val creditsMergeMode: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.creditsMergeMode] ?: false }

    override suspend fun setOfflineOnly(value: Boolean) { context.settingsDataStore.edit { it[Keys.offlineOnly] = value } }
    override suspend fun setRatingStep(value: Double) { context.settingsDataStore.edit { it[Keys.ratingStep] = value } }
    override suspend fun setRatingScale(value: String) { context.settingsDataStore.edit { it[Keys.ratingScale] = value } }
    override suspend fun setScoreMode(value: String) { context.settingsDataStore.edit { it[Keys.scoreMode] = value } }
    override suspend fun setGridView(value: Boolean) { context.settingsDataStore.edit { it[Keys.gridView] = value } }
    override suspend fun setDynamicColor(value: Boolean) { context.settingsDataStore.edit { it[Keys.dynamicColor] = value } }
    override suspend fun setSortOrder(value: String) { context.settingsDataStore.edit { it[Keys.sortOrder] = value } }
    override suspend fun setUnfinishedOnly(value: Boolean) { context.settingsDataStore.edit { it[Keys.unfinishedOnly] = value } }
    override suspend fun setDiscogsEnabled(value: Boolean) { context.settingsDataStore.edit { it[Keys.discogsEnabled] = value } }
    override suspend fun setDiscogsToken(value: String) { context.settingsDataStore.edit { it[Keys.discogsToken] = TokenCipher.encrypt(value.trim()) } }
    override suspend fun setLastFmEnabled(value: Boolean) { context.settingsDataStore.edit { it[Keys.lastFmEnabled] = value } }
    override suspend fun setLastFmApiKey(value: String) { context.settingsDataStore.edit { it[Keys.lastFmApiKey] = TokenCipher.encrypt(value.trim()) } }
    override suspend fun setGeniusEnabled(value: Boolean) { context.settingsDataStore.edit { it[Keys.geniusEnabled] = value } }
    override suspend fun setGeniusToken(value: String) { context.settingsDataStore.edit { it[Keys.geniusToken] = TokenCipher.encrypt(value.trim()) } }
    override suspend fun setShowCreditSources(value: Boolean) { context.settingsDataStore.edit { it[Keys.showCreditSources] = value } }
    override suspend fun setCreditSourceOrder(value: String) { context.settingsDataStore.edit { it[Keys.creditSourceOrder] = value } }
    override suspend fun setActiveCreditSources(value: String) { context.settingsDataStore.edit { it[Keys.activeCreditSources] = value } }
    override suspend fun setCreditsMergeMode(value: Boolean) { context.settingsDataStore.edit { it[Keys.creditsMergeMode] = value } }
}

private object TokenCipher {
    private const val ALIAS = "youneko-rate-provider-tokens"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION_PREFIX = "v1:"

    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val body = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            VERSION_PREFIX + Base64.encodeToString(iv + body, Base64.NO_WRAP)
        }.getOrDefault(value)
    }

    fun decrypt(value: String): String {
        if (value.isBlank() || !value.startsWith(VERSION_PREFIX)) return value
        return runCatching {
            val data = Base64.decode(value.removePrefix(VERSION_PREFIX), Base64.NO_WRAP)
            val iv = data.copyOfRange(0, 12)
            val body = data.copyOfRange(12, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }
}
