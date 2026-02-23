package com.brk718.tracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scraper para Servientrega Colombia.
 *
 * La página pública (https://www.servientrega.com/wps/portal/rastreo-envio) carga los
 * datos dentro de un <iframe> que apunta a mobile.servientrega.com, el cual llama a una
 * REST API JSON interna.  En lugar de parsear HTML del portal WebSphere, llamamos
 * directamente a esa API.
 *
 * Endpoint:
 *   GET https://mobile.servientrega.com/Services/ShipmentTracking/api/envio/{guia}/1/es
 *
 * Respuesta relevante:
 *   {
 *     "estadoActual": "ENTREGADO",
 *     "movimientos": [
 *       { "fecha": "16/01/2026 12:55", "movimiento": "Guia generada",
 *         "ubicacion": "Medellin (Antioquia)", "estado": "Cerrado" },
 *       ...
 *     ]
 *   }
 */
@Singleton
class ServientregaScraper @Inject constructor() : CarrierScraper {

    companion object {
        private const val TAG = "ServientregaScraper"
        private const val API_BASE =
            "https://mobile.servientrega.com/Services/ShipmentTracking/api/envio"
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
                val url = "$API_BASE/$trackingNumber/1/es"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://mobile.servientrega.com")
                    .header("Referer",
                        "https://mobile.servientrega.com/WebSitePortal/RastreoEnvio.html")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()
                response.close()

                if (!response.isSuccessful || body.isNullOrBlank()) {
                    android.util.Log.w(TAG, "HTTP ${response.code} para $trackingNumber")
                    return@withContext CarrierScraperResult(
                        null, emptyList(), "HTTP ${response.code}")
                }

                val json = JSONObject(body)
                val status = json.optString("estadoActual").takeIf { it.isNotBlank() }

                val movimientos = json.optJSONArray("movimientos")
                val events = buildList {
                    if (movimientos != null) {
                        for (i in 0 until movimientos.length()) {
                            val m = movimientos.getJSONObject(i)
                            add(CarrierScraperEvent(
                                timestamp   = m.optString("fecha"),
                                description = m.optString("movimiento")
                                    .ifBlank { m.optString("estado") },
                                location    = m.optString("ubicacion")
                            ))
                        }
                    }
                }

                if (status == null && events.isEmpty()) {
                    android.util.Log.w(TAG,
                        "Sin datos para $trackingNumber — respuesta: $body")
                    return@withContext CarrierScraperResult(
                        null, emptyList(), "Sin datos para $trackingNumber")
                }

                android.util.Log.d(TAG,
                    "OK: status=$status, eventos=${events.size} para $trackingNumber")
                CarrierScraperResult(status, events)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error scraping $trackingNumber: ${e.message}", e)
                CarrierScraperResult(null, emptyList(), e.message)
            }
        }
}
