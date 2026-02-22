package com.brk718.tracker.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val KEY_QUIET_HOURS_ENABLED    = booleanPreferencesKey("quiet_hours_enabled")
        val KEY_QUIET_HOURS_START        = intPreferencesKey("quiet_hours_start")
        val KEY_QUIET_HOURS_START_MINUTE = intPreferencesKey("quiet_hours_start_minute")
        val KEY_QUIET_HOURS_END          = intPreferencesKey("quiet_hours_end")
        val KEY_QUIET_HOURS_END_MINUTE   = intPreferencesKey("quiet_hours_end_minute")
        val KEY_AUTO_SYNC              = booleanPreferencesKey("auto_sync")
        val KEY_SYNC_INTERVAL_HOURS    = intPreferencesKey("sync_interval_hours")
        val KEY_SYNC_ONLY_WIFI         = booleanPreferencesKey("sync_only_wifi")
        val KEY_THEME                  = stringPreferencesKey("theme")
        val KEY_IS_PREMIUM             = booleanPreferencesKey("is_premium")
        val KEY_ONBOARDING_DONE        = booleanPreferencesKey("onboarding_done")
        val KEY_DELIVERED_COUNT        = intPreferencesKey("delivered_count")
        val KEY_TOTAL_TRACKED          = intPreferencesKey("total_tracked")
    }

    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            onlyImportantEvents  = prefs[KEY_ONLY_IMPORTANT_EVENTS] ?: false,
            quietHoursEnabled    = prefs[KEY_QUIET_HOURS_ENABLED] ?: false,
            quietHoursStart        = prefs[KEY_QUIET_HOURS_START] ?: 23,
            quietHoursStartMinute  = prefs[KEY_QUIET_HOURS_START_MINUTE] ?: 0,
            quietHoursEnd          = prefs[KEY_QUIET_HOURS_END] ?: 7,
            quietHoursEndMinute    = prefs[KEY_QUIET_HOURS_END_MINUTE] ?: 0,
            autoSync             = prefs[KEY_AUTO_SYNC] ?: true,
            syncIntervalHours    = prefs[KEY_SYNC_INTERVAL_HOURS] ?: 2,
            syncOnlyOnWifi       = prefs[KEY_SYNC_ONLY_WIFI] ?: false,
            theme                = prefs[KEY_THEME] ?: "system",
            isPremium            = prefs[KEY_IS_PREMIUM] ?: false,
            onboardingDone       = prefs[KEY_ONBOARDING_DONE] ?: false,
            deliveredCount       = prefs[KEY_DELIVERED_COUNT] ?: 0,
            totalTracked         = prefs[KEY_TOTAL_TRACKED] ?: 0
        )
    }

    /** Alias para compatibilidad con código que usa .userPreferencesFlow */
    val userPreferencesFlow: Flow<UserPreferences> get() = preferences

    suspend fun setNotificationsEnabled(value: Boolean) {
        dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = value }
    }

    suspend fun setOnlyImportantEvents(value: Boolean) {
        dataStore.edit { it[KEY_ONLY_IMPORTANT_EVENTS] = value }
    }

    suspend fun setQuietHoursEnabled(value: Boolean) {
        dataStore.edit { it[KEY_QUIET_HOURS_ENABLED] = value }
    }

    suspend fun setQuietHoursStart(hour: Int) {
        dataStore.edit { it[KEY_QUIET_HOURS_START] = hour }
    }

    suspend fun setQuietHoursStartMinute(minute: Int) {
        dataStore.edit { it[KEY_QUIET_HOURS_START_MINUTE] = minute }
    }

    suspend fun setQuietHoursEnd(hour: Int) {
        dataStore.edit { it[KEY_QUIET_HOURS_END] = hour }
    }

    suspend fun setQuietHoursEndMinute(minute: Int) {
        dataStore.edit { it[KEY_QUIET_HOURS_END_MINUTE] = minute }
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
        dataStore.edit { prefs ->
            prefs[KEY_IS_PREMIUM] = value
            // Al perder premium, resetear configuraciones exclusivas de forma atómica
            if (!value && (prefs[KEY_SYNC_INTERVAL_HOURS] ?: 2) == -1) {
                prefs[KEY_SYNC_INTERVAL_HOURS] = 2  // volver a 2h (default free)
            }
        }
    }

    suspend fun setOnboardingDone(value: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = value }
    }

    suspend fun incrementDeliveredCount() {
        dataStore.edit { prefs ->
            prefs[KEY_DELIVERED_COUNT] = (prefs[KEY_DELIVERED_COUNT] ?: 0) + 1
        }
    }

    suspend fun incrementTotalTracked() {
        dataStore.edit { prefs ->
            prefs[KEY_TOTAL_TRACKED] = (prefs[KEY_TOTAL_TRACKED] ?: 0) + 1
        }
    }

    /** Backfills stats from Room if both counters are still 0 (first launch after feature addition). */
    suspend fun syncStatsFromRoom(totalInRoom: Int, deliveredInRoom: Int) {
        dataStore.edit { prefs ->
            val currentTotal = prefs[KEY_TOTAL_TRACKED] ?: 0
            val currentDelivered = prefs[KEY_DELIVERED_COUNT] ?: 0
            if (currentTotal == 0 && totalInRoom > 0) {
                prefs[KEY_TOTAL_TRACKED] = totalInRoom
            }
            if (currentDelivered == 0 && deliveredInRoom > 0) {
                prefs[KEY_DELIVERED_COUNT] = deliveredInRoom
            }
        }
    }
}
