package com.brk718.tracker.data.remote

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scraper para Redpack México usando el endpoint WordPress AJAX de su sitio web.
 *
 * Flujo:
 *  1. GET https://www.redpack.com.mx/es/rastreo/?guia=<numero> → extraer nonce y action
 *     del JavaScript embebido (variable internacionalRedpackVars o similar).
 *  2. POST https://www.redpack.com.mx/wp-admin/admin-ajax.php con nonce + guia.
 *
 * Los keys exactos (nonce, action, y estructura del JSON de respuesta) se confirman
 * con los logs "nonce=... action=..." y "Keys raíz:" en el primer run real.
 */
@Singleton
class RedpackScraper @Inject constructor(
    @ApplicationContext private val context: Context
) : CarrierScraper {

    companion object {
        private const val TAG = "RedpackScraper"
        private const val TRACKING_URL = "https://www.redpack.com.mx/es/rastreo/"
        private const val AJAX_URL = "https://www.redpack.com.mx/wp-admin/admin-ajax.php"
        private const val TIMEOUT_S = 15L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

        // Patrones para extraer nonce y action del JavaScript de la página.
        // El contexto "internacionalRedpackVars" se ve en el HTML de la página de rastreo.
        private val NONCE_REGEX  = Regex("""["']nonce["']\s*:\s*["']([^"']+)["']""")
        private val ACTION_REGEX = Regex("""["']action["']\s*:\s*["']([^"']+)["']""")
    }

    // CookieJar para mantener las cookies de sesión de WordPress entre las dos peticiones
    private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies.toMutableList()
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host] ?: emptyList()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .followRedirects(true)
        .build()

    override suspend fun getTracking(trackingNumber: String): CarrierScraperResult =
        withContext(Dispatchers.IO) {
            try {
                // ── Paso 1: GET página para extraer nonce y action ─────────────
                val pageHtml = client.newCall(
                    Request.Builder()
                        .url("$TRACKING_URL?guia=$trackingNumber")
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.9")
                        .header("Accept-Language", "es-MX,es;q=0.9")
                        .build()
                ).execute().use { it.body?.string() }
                    ?: return@withContext CarrierScraperResult(null, emptyList(), "Página vacía")

                val nonce  = NONCE_REGEX.find(pageHtml)?.groupValues?.getOrNull(1)
                val action = ACTION_REGEX.find(pageHtml)?.groupValues?.getOrNull(1)
                Log.d(TAG, "nonce=$nonce  action=$action")

                // ── Paso 2: POST AJAX con nonce ────────────────────────────────
                val bodyBuilder = FormBody.Builder()
                    .add("action", action ?: "redpack_rastreo")  // fallback; Logcat revelará el real
                    .add("guia", trackingNumber)
                if (nonce != null) {
                    bodyBuilder
                        .add("nonce",       nonce)
                        .add("security",    nonce)   // WordPress también acepta este campo
                        .add("_ajax_nonce", nonce)   // alias alternativo
                }

                val response = client.newCall(
                    Request.Builder()
                        .url(AJAX_URL)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json, text/javascript, */*")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "https://www.redpack.com.mx")
                        .header("Referer", "$TRACKING_URL?guia=$trackingNumber")
                        .post(bodyBuilder.build())
                        .build()
                ).execute()

                val rawBody = response.body?.string()
                Log.d(TAG, "AJAX ${response.code} — body=${rawBody?.take(500)}")

                if (!response.isSuccessful || rawBody.isNullOrBlank()) {
                    return@withContext CarrierScraperResult(null, emptyList(), "HTTP ${response.code}")
                }

                parseResponse(rawBody, trackingNumber)

            } catch (e: Exception) {
                Log.e(TAG, "Error scraping $trackingNumber: ${e.message}", e)
                CarrierScraperResult(null, emptyList(), e.message)
            }
        }

    private fun parseResponse(json: String, trackingNumber: String): CarrierScraperResult {
        return try {
            val obj = JSONObject(json)
            Log.d(TAG, "Keys raíz: ${obj.keys().asSequence().toList()}")

            // Estado — probar keys comunes; Logcat confirmará cuál es el correcto
            val status = obj.optString("status")
                .ifBlank { obj.optString("estado") }
                .ifBlank { obj.optJSONObject("data")?.optString("status") ?: "" }
                .takeIf { it.isNotBlank() }

            // Array de eventos — keys a confirmar con el primer log real
            val eventsArr = obj.optJSONArray("events")
                ?: obj.optJSONArray("history")
                ?: obj.optJSONArray("data")
                ?: obj.optJSONObject("data")?.optJSONArray("events")
                ?: obj.optJSONObject("data")?.optJSONArray("history")

            val events = mutableListOf<CarrierScraperEvent>()
            if (eventsArr != null) {
                for (i in 0 until eventsArr.length()) {
                    val ev = eventsArr.optJSONObject(i) ?: continue
                    if (i == 0) Log.d(TAG, "evento[0] keys: ${ev.keys().asSequence().toList()}")
                    val ts   = ev.optString("date").ifBlank { ev.optString("fecha") }
                    val desc = ev.optString("description")
                        .ifBlank { ev.optString("status") }
                        .ifBlank { ev.optString("event") }
                    val loc  = ev.optString("location").ifBlank { ev.optString("ciudad") }
                    if (desc.isNotBlank()) events.add(CarrierScraperEvent(ts, desc, loc))
                }
            }

            if (status == null && events.isEmpty()) {
                Log.w(TAG, "Sin datos para $trackingNumber — JSON:\n${json.take(1000)}")
                CarrierScraperResult(null, emptyList(), "Sin datos (revisar action/nonce en Logcat)")
            } else {
                Log.d(TAG, "OK: status=$status, eventos=${events.size}")
                CarrierScraperResult(status, events)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseResponse error: ${e.message}")
            CarrierScraperResult(null, emptyList(), e.message)
        }
    }
}
