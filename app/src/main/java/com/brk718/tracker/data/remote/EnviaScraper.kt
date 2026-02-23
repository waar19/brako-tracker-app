package com.brk718.tracker.data.remote

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scraper para Envía/Colvanes usando su API pública sin autenticación.
 *
 * Endpoint: POST https://queries.envia.com/shipments/generaltrack?is_landing=true
 * Body: {"trackingNumbers": ["<guia>"]}
 * Sin token, sin cookies, sin CSRF.
 *
 * Los keys exactos del JSON se confirman con el log "Keys raíz:" en el primer run.
 */
@Singleton
class EnviaScraper @Inject constructor(
    @ApplicationContext private val context: Context
) : CarrierScraper {

    companion object {
        private const val TAG = "EnviaScraper"
        private const val API_URL =
            "https://queries.envia.com/shipments/generaltrack?is_landing=true"
        private const val TIMEOUT_S = 15L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Mobile Safari/537.36"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun getTracking(trackingNumber: String): CarrierScraperResult =
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = JSONObject()
                    .put("trackingNumbers", JSONArray().put(trackingNumber))
                    .toString()

                val request = Request.Builder()
                    .url(API_URL)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "es-CO")
                    .header("Origin", "https://envia.com")
                    .header("Referer", "https://envia.com/es-CO/rastreo?label=$trackingNumber")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()
                Log.d(TAG, "HTTP ${response.code} para $trackingNumber — body=${body?.take(500)}")

                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext CarrierScraperResult(
                        null, emptyList(), "HTTP ${response.code}"
                    )
                }

                parseResponse(body, trackingNumber)

            } catch (e: Exception) {
                Log.e(TAG, "Error scraping $trackingNumber: ${e.message}", e)
                CarrierScraperResult(null, emptyList(), e.message)
            }
        }

    private fun parseResponse(json: String, trackingNumber: String): CarrierScraperResult {
        return try {
            val arr = JSONArray(json)
            if (arr.length() == 0) {
                Log.d(TAG, "Sin datos para $trackingNumber (array vacío)")
                return CarrierScraperResult(null, emptyList(), "Sin datos para $trackingNumber")
            }

            val item = arr.getJSONObject(0)
            Log.d(TAG, "Keys raíz: ${item.keys().asSequence().toList()}")

            // Estado actual — probar keys comunes; los definitivos se confirman con el log de arriba
            val status = item.optString("status")
                .ifBlank { item.optString("estado") }
                .ifBlank { item.optString("statusCode") }
                .ifBlank { item.optString("trackingStatus") }
                .takeIf { it.isNotBlank() }

            // Array de eventos históricos — keys a confirmar con el primer log
            val eventsArr = item.optJSONArray("events")
                ?: item.optJSONArray("history")
                ?: item.optJSONArray("trackingHistory")
                ?: item.optJSONArray("novedades")
                ?: item.optJSONArray("movimientos")

            val events = mutableListOf<CarrierScraperEvent>()
            if (eventsArr != null) {
                for (i in 0 until eventsArr.length()) {
                    val ev = eventsArr.optJSONObject(i) ?: continue
                    if (i == 0) Log.d(TAG, "evento[0] keys: ${ev.keys().asSequence().toList()}")

                    val ts = ev.optString("date")
                        .ifBlank { ev.optString("fecha") }
                        .ifBlank { ev.optString("createdAt") }
                        .ifBlank { ev.optString("timestamp") }

                    val desc = ev.optString("description")
                        .ifBlank { ev.optString("status") }
                        .ifBlank { ev.optString("event") }
                        .ifBlank { ev.optString("novedad") }

                    val loc = ev.optString("location")
                        .ifBlank { ev.optString("city") }
                        .ifBlank { ev.optString("ciudad") }
                        .ifBlank { ev.optString("place") }

                    if (desc.isNotBlank()) {
                        events.add(CarrierScraperEvent(ts, desc, loc))
                    }
                }
            }

            if (status == null && events.isEmpty()) {
                Log.w(TAG, "JSON sin status ni eventos para $trackingNumber")
                CarrierScraperResult(null, emptyList(), "Sin datos (revisar keys del JSON)")
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
