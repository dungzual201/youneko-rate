package com.youneko.rate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "youneko_settings")

interface SettingsStore {
    val offlineOnly: Flow<Boolean>
    val ratingStep: Flow<Double>
    val scoreMode: Flow<String>
    val gridView: Flow<Boolean>
    val dynamicColor: Flow<Boolean>
    val sortOrder: Flow<String>
    val unfinishedOnly: Flow<Boolean>
    val discogsEnabled: Flow<Boolean>
    val discogsToken: Flow<String>
    val lastFmEnabled: Flow<Boolean>
    val lastFmApiKey: Flow<String>

    suspend fun setOfflineOnly(value: Boolean)
    suspend fun setRatingStep(value: Double)
    suspend fun setScoreMode(value: String)
    suspend fun setGridView(value: Boolean)
    suspend fun setDynamicColor(value: Boolean)
    suspend fun setSortOrder(value: String)
    suspend fun setUnfinishedOnly(value: Boolean)
    suspend fun setDiscogsEnabled(value: Boolean)
    suspend fun setDiscogsToken(value: String)
    suspend fun setLastFmEnabled(value: Boolean)
    suspend fun setLastFmApiKey(value: String)
}

class SettingsDataStore(private val context: Context) : SettingsStore {
    private object Keys {
        val offlineOnly = booleanPreferencesKey("offline_only")
        val ratingStep = doublePreferencesKey("rating_step")
        val scoreMode = stringPreferencesKey("score_mode")
        val gridView = booleanPreferencesKey("library_grid_view")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val sortOrder = stringPreferencesKey("library_sort_order")
        val unfinishedOnly = booleanPreferencesKey("library_unfinished_only")
        val discogsEnabled = booleanPreferencesKey("provider_discogs_enabled")
        val discogsToken = stringPreferencesKey("provider_discogs_token")
        val lastFmEnabled = booleanPreferencesKey("provider_lastfm_enabled")
        val lastFmApiKey = stringPreferencesKey("provider_lastfm_api_key")
    }

    override val offlineOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.offlineOnly] ?: false }
    override val ratingStep: Flow<Double> = context.settingsDataStore.data.map { it[Keys.ratingStep] ?: 0.5 }
    override val scoreMode: Flow<String> = context.settingsDataStore.data.map { it[Keys.scoreMode] ?: "SIMPLE" }
    override val gridView: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.gridView] ?: true }
    override val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.dynamicColor] ?: false }
    override val sortOrder: Flow<String> = context.settingsDataStore.data.map { it[Keys.sortOrder] ?: "NEWEST" }
    override val unfinishedOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.unfinishedOnly] ?: false }
    override val discogsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.discogsEnabled] ?: false }
    override val discogsToken: Flow<String> = context.settingsDataStore.data.map { it[Keys.discogsToken].orEmpty() }
    override val lastFmEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.lastFmEnabled] ?: false }
    override val lastFmApiKey: Flow<String> = context.settingsDataStore.data.map { it[Keys.lastFmApiKey].orEmpty() }

    override suspend fun setOfflineOnly(value: Boolean) { context.settingsDataStore.edit { it[Keys.offlineOnly] = value } }
    override suspend fun setRatingStep(value: Double) { context.settingsDataStore.edit { it[Keys.ratingStep] = value } }
    override suspend fun setScoreMode(value: String) { context.settingsDataStore.edit { it[Keys.scoreMode] = value } }
    override suspend fun setGridView(value: Boolean) { context.settingsDataStore.edit { it[Keys.gridView] = value } }
    override suspend fun setDynamicColor(value: Boolean) { context.settingsDataStore.edit { it[Keys.dynamicColor] = value } }
    override suspend fun setSortOrder(value: String) { context.settingsDataStore.edit { it[Keys.sortOrder] = value } }
    override suspend fun setUnfinishedOnly(value: Boolean) { context.settingsDataStore.edit { it[Keys.unfinishedOnly] = value } }
    override suspend fun setDiscogsEnabled(value: Boolean) { context.settingsDataStore.edit { it[Keys.discogsEnabled] = value } }
    override suspend fun setDiscogsToken(value: String) { context.settingsDataStore.edit { it[Keys.discogsToken] = value.trim() } }
    override suspend fun setLastFmEnabled(value: Boolean) { context.settingsDataStore.edit { it[Keys.lastFmEnabled] = value } }
    override suspend fun setLastFmApiKey(value: String) { context.settingsDataStore.edit { it[Keys.lastFmApiKey] = value.trim() } }
}
