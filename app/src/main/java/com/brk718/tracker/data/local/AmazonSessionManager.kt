package com.brk718.tracker.data.local

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmazonSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "amazon_session"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_LAST_UPDATE = "last_update"
        // Cookies clave para la sesión de Amazon
        private val SESSION_COOKIES = listOf("x-main", "at-main", "sess-at-main", "ubid-main")
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCookies(url: String) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url) ?: return
        
        // Guardar la cadena de cookies completa para simplificar
        prefs.edit()
            .putString(KEY_COOKIES, cookies)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    fun getCookies(): String? {
        return prefs.getString(KEY_COOKIES, null)
    }

    fun isLoggedIn(): Boolean {
        val cookies = getCookies() ?: return false
        // Verificación básica: debe contener cookies de sesión clave
        return SESSION_COOKIES.any { cookies.contains(it) }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }
}
