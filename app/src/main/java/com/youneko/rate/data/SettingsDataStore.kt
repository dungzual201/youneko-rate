package com.youneko.rate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "youneko_settings")

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val offlineOnly = booleanPreferencesKey("offline_only")
        val ratingStep = doublePreferencesKey("rating_step")
    }

    val offlineOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.offlineOnly] ?: false }
    val ratingStep: Flow<Double> = context.settingsDataStore.data.map { it[Keys.ratingStep] ?: 0.5 }

    suspend fun setOfflineOnly(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.offlineOnly] = value }
    }

    suspend fun setRatingStep(value: Double) {
        context.settingsDataStore.edit { it[Keys.ratingStep] = value }
    }
}
