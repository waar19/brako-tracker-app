package com.brk718.tracker.data.local

data class UserPreferences(
    val notificationsEnabled: Boolean = true,
    val onlyImportantEvents: Boolean = false,
    val autoSync: Boolean = true,
    val syncIntervalHours: Int = 2,   // 1, 2, 6, 12 | 0 = solo manual
    val syncOnlyOnWifi: Boolean = false,
    val theme: String = "system"       // "light", "dark", "system"
)
