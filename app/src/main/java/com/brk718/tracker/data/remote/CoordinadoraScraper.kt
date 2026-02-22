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
 * Scraper para Coordinadora Colombia.
 *
 * URL pública: https://coordinadora.com/rastreo/rastreo-de-guia/detalle-de-rastreo-de-guia/?guia={guia}
 *
 * Estrategia (en orden):
 *  1. Buscar JSON embebido en script tags (__NEXT_DATA__, application/json, window.*) — los
 *     sitios React/Next.js incluyen el estado inicial como JSON en la página.
 *  2. Parsear HTML con Jsoup buscando selectores de eventos/novedades.
 *  3. Loguear HTML truncado para depuración si no se encuentran datos.
 *
 * NOTA: Si Coordinadora cambia el layout, ajustar [parseJsonScript] y [parseHtmlEvents].
 */
@Singleton
class CoordinadoraScraper @Inject constructor(
    @ApplicationContext private val context: Context
) : ColombianCarrierScraper {

    companion object {
        private const val TAG = "CoordinadoraScraper"
        private const val BASE_URL =
            "https://coordinadora.com/rastreo/rastreo-de-guia/detalle-de-rastreo-de-guia/"
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

                // Intento 1: JSON embebido en script tag (React / Next.js)
                val jsonResult = parseJsonScript(doc, trackingNumber)
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

    // ─── Intento 1: JSON en script tags ───────────────────────────────────────

    private fun parseJsonScript(doc: Document, trackingNumber: String): CarrierScraperResult? {
        // Buscar __NEXT_DATA__ (Next.js SSR) o cualquier script con JSON de tracking
        val scriptSelectors = listOf(
            "script#__NEXT_DATA__",
            "script[type='application/json']",
            "script[type=\"application/json\"]",
        )

        for (selector in scriptSelectors) {
            val script = doc.selectFirst(selector) ?: continue
            try {
                val json = JSONObject(script.html())
                // Intentar navegar la estructura común de Next.js:
                // { props: { pageProps: { tracking: { estado, novedades: [...] } } } }
                val tracking = json
                    .optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("tracking")
                    ?: continue

                val status = tracking.optString("estado").takeIf { it.isNotBlank() }
                val novedadesArr = tracking.optJSONArray("novedades")
                    ?: tracking.optJSONArray("events")
                    ?: tracking.optJSONArray("eventos")

                val events = mutableListOf<CarrierScraperEvent>()
                if (novedadesArr != null) {
                    for (i in 0 until novedadesArr.length()) {
                        val item = novedadesArr.optJSONObject(i) ?: continue
                        val timestamp = item.optString("fecha")
                            .ifBlank { item.optString("date").ifBlank { item.optString("hora") } }
                        val description = item.optString("descripcion")
                            .ifBlank { item.optString("description").ifBlank { item.optString("novedad") } }
                        val location = item.optString("ciudad")
                            .ifBlank { item.optString("city").ifBlank { item.optString("ubicacion") } }
                        if (description.isNotBlank()) {
                            events.add(CarrierScraperEvent(timestamp, description, location))
                        }
                    }
                }

                if (status != null || events.isNotEmpty()) {
                    android.util.Log.d(TAG, "JSON encontrado — status=$status, eventos=${events.size}")
                    return CarrierScraperResult(status, events)
                }
            } catch (_: Exception) {
                // JSON malformado o estructura distinta, probar siguiente
            }
        }
        return null
    }

    // ─── Intento 2: HTML con selectores ───────────────────────────────────────

    private fun parseHtmlStatus(doc: Document): String? {
        val selectors = listOf(
            "[class*=estado-guia]",
            "[class*=estado-envio]",
            "[class*=estado-actual]",
            "[class*=estado]",
            "[class*=status-tracking]",
            ".alert-info",
            ".badge",
            "h3", "h4",
        )
        for (selector in selectors) {
            val text = doc.selectFirst(selector)?.text()?.trim()
                ?.takeIf { it.isNotBlank() && it.length in 4..80 }
            if (text != null) {
                android.util.Log.d(TAG, "Estado con '$selector': $text")
                return text
            }
        }
        return null
    }

    private fun parseHtmlEvents(doc: Document): List<CarrierScraperEvent> {
        // Prioridad: elementos con "novedad", "evento", "rastreo" en clase
        val containerSelectors = listOf(
            "[class*=novedad]",
            "[class*=evento-rastreo]",
            "[class*=detalle-rastreo]",
            "[class*=tracking-event]",
            "table[class*=rastreo]",
            "table[class*=tracking]",
            ".card-body",
            "table",
        )

        for (selector in containerSelectors) {
            val container = doc.selectFirst(selector) ?: continue

            // Si es una tabla
            val rows = container.select("tr").drop(1)
            if (rows.isNotEmpty()) {
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

            // Si son divs/list items con texto
            val items = container.select("[class*=novedad], [class*=evento], li, .row")
            val events = items.mapNotNull { item ->
                val text = item.text().trim()
                if (text.isBlank()) return@mapNotNull null
                CarrierScraperEvent("", text, "")
            }
            if (events.isNotEmpty()) return events
        }

        return emptyList()
    }
}
