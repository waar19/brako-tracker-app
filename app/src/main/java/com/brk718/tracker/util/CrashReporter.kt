package com.brk718.tracker.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Wrapper seguro para Crashlytics.
 * Si Firebase no está configurado (google-services.json placeholder),
 * solo loguea localmente sin crashear.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"

    fun log(message: String) {
        try {
            FirebaseCrashlytics.getInstance().log(message)
        } catch (e: Exception) {
            Log.d(TAG, message)
        }
    }

    fun recordException(throwable: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${throwable.message}", throwable)
        }
    }

    fun setUserId(id: String) {
        try {
            FirebaseCrashlytics.getInstance().setUserId(id)
        } catch (e: Exception) {
            Log.d(TAG, "setUserId: $id")
        }
    }

    fun setCustomKey(key: String, value: String) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Exception) {
            Log.d(TAG, "setCustomKey: $key=$value")
        }
    }
}
