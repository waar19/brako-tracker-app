package com.brk718.tracker.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scraper para Deprisa Colombia (subsidiaria de Avianca).
 *
 * URL pública: https://www.deprisa.com/rastrear/?guia={guia}
 *
 * Estrategia (en orden):
 *  1. Buscar JSON embebido en script tags — Deprisa puede usar un SPA con SSR.
 *  2. Parsear HTML con Jsoup buscando selectores de eventos/novedades.
 *  3. Loguear HTML truncado para depuración si no se encuentran datos.
 *
 * NOTA: Si Deprisa cambia el layout, ajustar [parseJsonScript] y [parseHtmlEvents].
 */
@Singleton
class DeprisaScraper @Inject constructor(
    @ApplicationContext private val context: Context
) : CarrierScraper {

    companion object {
        private const val TAG = "DeprisaScraper"
        private const val BASE_URL = "https://www.deprisa.com/rastrear/"
        private const val TIMEOUT_S = 15L
        private const val DEBUG_HTML_MAX_CHARS = 3_000

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Mobile Safari/537.36"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun getTracking(trackingNumber: String): CarrierScraperResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL?guia=$trackingNumber"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "es-CO,es;q=0.9,en;q=0.8")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    android.util.Log.w(TAG, "HTTP ${response.code} para $trackingNumber")
                    return@withContext CarrierScraperResult(null, emptyList(),
                        "HTTP ${response.code}")
                }

                val rawBody = response.body?.string()
                response.close()
                if (rawBody == null) return@withContext CarrierScraperResult(null, emptyList(), "Respuesta vacía")
                val html = rawBody

                val doc = Jsoup.parse(html)

                // Intento 1: JSON embebido (SPA con SSR)
                val jsonResult = parseJsonScript(doc)
                if (jsonResult != null) return@withContext jsonResult

                // Intento 2: HTML con Jsoup
                val status = parseHtmlStatus(doc)
                val events = parseHtmlEvents(doc)

                if (status == null && events.isEmpty()) {
                    android.util.Log.d(TAG,
                        "Sin datos para $trackingNumber — HTML (${html.length} chars): " +
                        html.take(DEBUG_HTML_MAX_CHARS))
                    return@withContext CarrierScraperResult(null, emptyList(),
                        "Sin datos (revisar selectores)")
                }

                android.util.Log.d(TAG,
                    "OK: status=$status, eventos=${events.size} para $trackingNumber")
                CarrierScraperResult(status, events)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error scraping $trackingNumber: ${e.message}", e)
                CarrierScraperResult(null, emptyList(), e.message)
            }
        }

    // ─── JSON embebido (React / Next.js / Angular) ─────────────────────────

    private fun parseJsonScript(doc: Document): CarrierScraperResult? {
        val scriptSelectors = listOf(
            "script#__NEXT_DATA__",
            "script[type='application/json']",
            "script[type=\"application/json\"]",
        )
        for (selector in scriptSelectors) {
            val script = doc.selectFirst(selector) ?: continue
            try {
                val json = JSONObject(script.html())
                // Next.js: props.pageProps.tracking o similar
                val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps")
                    ?: continue
                val tracking = pageProps.optJSONObject("tracking")
                    ?: pageProps.optJSONObject("rastreo")
                    ?: pageProps.optJSONObject("guia")
                    ?: continue

                val status = tracking.optString("estado")
                    .ifBlank { tracking.optString("estado_actual").ifBlank { null } }
                val eventsArr = tracking.optJSONArray("novedades")
                    ?: tracking.optJSONArray("eventos")
                    ?: tracking.optJSONArray("eventos_rastreo")

                val events = mutableListOf<CarrierScraperEvent>()
                if (eventsArr != null) {
                    for (i in 0 until eventsArr.length()) {
                        val item = eventsArr.optJSONObject(i) ?: continue
                        val timestamp = item.optString("fecha_hora")
                            .ifBlank { item.optString("fecha").ifBlank { item.optString("date") } }
                        val description = item.optString("descripcion")
                            .ifBlank { item.optString("novedad").ifBlank { item.optString("description") } }
                        val location = item.optString("ciudad")
                            .ifBlank { item.optString("city").ifBlank { "" } }
                        if (description.isNotBlank()) {
                            events.add(CarrierScraperEvent(timestamp, description, location))
                        }
                    }
                }
                if (status != null || events.isNotEmpty()) {
                    android.util.Log.d(TAG, "JSON encontrado — status=$status, eventos=${events.size}")
                    return CarrierScraperResult(status, events)
                }
            } catch (_: Exception) { }
        }
        return null
    }

    // ─── HTML con selectores ───────────────────────────────────────────────

    private fun parseHtmlStatus(doc: Document): String? {
        val selectors = listOf(
            "[class*=estado-guia]",
            "[class*=estado-envio]",
            "[class*=estado]",
            "[class*=status-envio]",
            ".tracking-status",
            ".alert-info",
            ".badge-status",
        )
        for (selector in selectors) {
            val text = doc.selectFirst(selector)?.text()?.trim()
                ?.takeIf { it.isNotBlank() && it.length in 4..100 }
            if (text != null) {
                android.util.Log.d(TAG, "Estado con '$selector': $text")
                return text
            }
        }
        return null
    }

    private fun parseHtmlEvents(doc: Document): List<CarrierScraperEvent> {
        val tableSelectors = listOf(
            "table[class*=rastreo]",
            "table[class*=tracking]",
            "table.table",
            ".rastreo table",
            ".resultado-rastreo table",
            "table",
        )
        for (selector in tableSelectors) {
            val table = doc.selectFirst(selector) ?: continue
            val rows = table.select("tr").drop(1)
            if (rows.isEmpty()) continue
            val events = rows.mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 2) return@mapNotNull null
                val timestamp = cells.getOrNull(0)?.text()?.trim() ?: ""
                val description = cells.getOrNull(1)?.text()?.trim() ?: ""
                val location = cells.getOrNull(2)?.text()?.trim() ?: ""
                if (description.isBlank()) return@mapNotNull null
                CarrierScraperEvent(timestamp, description, location)
            }
            if (events.isNotEmpty()) return events
        }

        for (selector in listOf("[class*=novedad]", "[class*=evento]", "[class*=detalle]")) {
            val items = doc.select(selector)
            val events = items.mapNotNull {
                val text = it.text().trim().takeIf { t -> t.isNotBlank() } ?: return@mapNotNull null
                CarrierScraperEvent("", text, "")
            }
            if (events.isNotEmpty()) return events
        }

        return emptyList()
    }
}
