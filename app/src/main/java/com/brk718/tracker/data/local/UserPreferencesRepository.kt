package com.brk718.tracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_NOTIFICATIONS_ENABLED  = booleanPreferencesKey("notifications_enabled")
        val KEY_ONLY_IMPORTANT_EVENTS  = booleanPreferencesKey("only_important_events")
        val KEY_AUTO_SYNC              = booleanPreferencesKey("auto_sync")
        val KEY_SYNC_INTERVAL_HOURS    = intPreferencesKey("sync_interval_hours")
        val KEY_SYNC_ONLY_WIFI         = booleanPreferencesKey("sync_only_wifi")
        val KEY_THEME                  = stringPreferencesKey("theme")
        val KEY_IS_PREMIUM             = booleanPreferencesKey("is_premium")
    }

    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            onlyImportantEvents  = prefs[KEY_ONLY_IMPORTANT_EVENTS] ?: false,
            autoSync             = prefs[KEY_AUTO_SYNC] ?: true,
            syncIntervalHours    = prefs[KEY_SYNC_INTERVAL_HOURS] ?: 2,
            syncOnlyOnWifi       = prefs[KEY_SYNC_ONLY_WIFI] ?: false,
            theme                = prefs[KEY_THEME] ?: "system",
            isPremium            = prefs[KEY_IS_PREMIUM] ?: false
        )
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = value }
    }

    suspend fun setOnlyImportantEvents(value: Boolean) {
        dataStore.edit { it[KEY_ONLY_IMPORTANT_EVENTS] = value }
    }

    suspend fun setAutoSync(value: Boolean) {
        dataStore.edit { it[KEY_AUTO_SYNC] = value }
    }

    suspend fun setSyncIntervalHours(value: Int) {
        dataStore.edit { it[KEY_SYNC_INTERVAL_HOURS] = value }
    }

    suspend fun setSyncOnlyOnWifi(value: Boolean) {
        dataStore.edit { it[KEY_SYNC_ONLY_WIFI] = value }
    }

    suspend fun setTheme(value: String) {
        dataStore.edit { it[KEY_THEME] = value }
    }

    suspend fun setIsPremium(value: Boolean) {
        dataStore.edit { it[KEY_IS_PREMIUM] = value }
    }
}
