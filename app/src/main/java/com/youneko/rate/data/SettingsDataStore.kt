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

    suspend fun setOfflineOnly(value: Boolean)
    suspend fun setRatingStep(value: Double)
    suspend fun setScoreMode(value: String)
    suspend fun setGridView(value: Boolean)
    suspend fun setDynamicColor(value: Boolean)
    suspend fun setSortOrder(value: String)
    suspend fun setUnfinishedOnly(value: Boolean)
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
    }

    override val offlineOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.offlineOnly] ?: false }
    override val ratingStep: Flow<Double> = context.settingsDataStore.data.map { it[Keys.ratingStep] ?: 0.5 }
    override val scoreMode: Flow<String> = context.settingsDataStore.data.map { it[Keys.scoreMode] ?: "SIMPLE" }
    override val gridView: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.gridView] ?: true }
    override val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.dynamicColor] ?: false }
    override val sortOrder: Flow<String> = context.settingsDataStore.data.map { it[Keys.sortOrder] ?: "NEWEST" }
    override val unfinishedOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.unfinishedOnly] ?: false }

    override suspend fun setOfflineOnly(value: Boolean) { context.settingsDataStore.edit { it[Keys.offlineOnly] = value } }
    override suspend fun setRatingStep(value: Double) { context.settingsDataStore.edit { it[Keys.ratingStep] = value } }
    override suspend fun setScoreMode(value: String) { context.settingsDataStore.edit { it[Keys.scoreMode] = value } }
    override suspend fun setGridView(value: Boolean) { context.settingsDataStore.edit { it[Keys.gridView] = value } }
    override suspend fun setDynamicColor(value: Boolean) { context.settingsDataStore.edit { it[Keys.dynamicColor] = value } }
    override suspend fun setSortOrder(value: String) { context.settingsDataStore.edit { it[Keys.sortOrder] = value } }
    override suspend fun setUnfinishedOnly(value: Boolean) { context.settingsDataStore.edit { it[Keys.unfinishedOnly] = value } }
}
