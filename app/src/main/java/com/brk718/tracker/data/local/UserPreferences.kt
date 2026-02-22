package com.brk718.tracker.data.local

data class UserPreferences(
    val notificationsEnabled: Boolean = true,
    val onlyImportantEvents: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 23,        // hora de inicio (0-23), por defecto 23:00
    val quietHoursStartMinute: Int = 0,   // minuto de inicio (0-59), por defecto :00
    val quietHoursEnd: Int = 7,           // hora de fin (0-23), por defecto 07:00
    val quietHoursEndMinute: Int = 0,     // minuto de fin (0-59), por defecto :00
    val autoSync: Boolean = true,
    val syncIntervalHours: Int = 2,
    val syncOnlyOnWifi: Boolean = false,
    val theme: String = "system",
    val isPremium: Boolean = false,
    val onboardingDone: Boolean = false,
    val deliveredCount: Int = 0,
    val totalTracked: Int = 0
)
