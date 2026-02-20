package com.brk718.tracker.data.local

data class UserPreferences(
    val notificationsEnabled: Boolean = true,
    val onlyImportantEvents: Boolean = false,
    val autoSync: Boolean = true,
    val syncIntervalHours: Int = 2,
    val syncOnlyOnWifi: Boolean = false,
    val theme: String = "system",
    val isPremium: Boolean = false,
    val onboardingDone: Boolean = false,
    val deliveredCount: Int = 0,
    val totalTracked: Int = 0
)
