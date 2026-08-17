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

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val offlineOnly = booleanPreferencesKey("offline_only")
        val ratingStep = doublePreferencesKey("rating_step")
        val scoreMode = stringPreferencesKey("score_mode")
        val gridView = booleanPreferencesKey("library_grid_view")
        val sortOrder = stringPreferencesKey("library_sort_order")
        val favoriteOnly = booleanPreferencesKey("library_favorite_only")
        val unfinishedOnly = booleanPreferencesKey("library_unfinished_only")
    }

    val offlineOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.offlineOnly] ?: false }
    val ratingStep: Flow<Double> = context.settingsDataStore.data.map { it[Keys.ratingStep] ?: 0.5 }
    val scoreMode: Flow<String> = context.settingsDataStore.data.map { it[Keys.scoreMode] ?: "SIMPLE" }
    val gridView: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.gridView] ?: true }
    val sortOrder: Flow<String> = context.settingsDataStore.data.map { it[Keys.sortOrder] ?: "NEWEST" }
    val favoriteOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.favoriteOnly] ?: false }
    val unfinishedOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.unfinishedOnly] ?: false }

    suspend fun setOfflineOnly(value: Boolean) { context.settingsDataStore.edit { it[Keys.offlineOnly] = value } }
    suspend fun setRatingStep(value: Double) { context.settingsDataStore.edit { it[Keys.ratingStep] = value } }
    suspend fun setScoreMode(value: String) { context.settingsDataStore.edit { it[Keys.scoreMode] = value } }
    suspend fun setGridView(value: Boolean) { context.settingsDataStore.edit { it[Keys.gridView] = value } }
    suspend fun setSortOrder(value: String) { context.settingsDataStore.edit { it[Keys.sortOrder] = value } }
    suspend fun setFavoriteOnly(value: Boolean) { context.settingsDataStore.edit { it[Keys.favoriteOnly] = value } }
    suspend fun setUnfinishedOnly(value: Boolean) { context.settingsDataStore.edit { it[Keys.unfinishedOnly] = value } }
}
