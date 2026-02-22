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
 * Scraper para Servientrega Colombia.
 *
 * URL pública: https://www.servientrega.com/wps/portal/rastreo-envio?tracking={guia}
 *
 * Estrategia (en orden):
 *  1. Buscar JSON embebido en script tags (__NEXT_DATA__, application/json) —
 *     el nuevo portal de Servientrega puede usar Next.js SSR.
 *  2. Parsear HTML con Jsoup buscando selectores del portal WPS y del nuevo diseño.
 *  3. Loguear HTML truncado para depuración si no se encuentran datos.
 *
 * NOTA: Si Servientrega cambia el layout, ajustar [parseJsonScript] y [parseStatus]/[parseEvents].
 */
@Singleton
class ServientregaScraper @Inject constructor(
    @ApplicationContext private val context: Context
) : ColombianCarrierScraper {

    companion object {
        private const val TAG = "ServientregaScraper"
        private const val BASE_URL = "https://www.servientrega.com/wps/portal/rastreo-envio"
        private const val TIMEOUT_S = 15L
        private const val DEBUG_HTML_MAX_CHARS = 3_000

        // User-Agent de Chrome Android para evitar bloqueos de bot-detection básico
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
                val url = "$BASE_URL?tracking=$trackingNumber"
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

                // Intento 1: JSON embebido (Next.js SSR u otro framework)
                val jsonResult = parseJsonScript(doc)
                if (jsonResult != null) return@withContext jsonResult

                // Intento 2: HTML con Jsoup
                val status = parseStatus(doc)
                val events = parseEvents(doc)

                if (status == null && events.isEmpty()) {
                    // Log HTML truncado para depuración
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

    // ─── JSON embebido (Next.js / React SSR) ──────────────────────────────────

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
                val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps") ?: continue
                val tracking = pageProps.optJSONObject("tracking")
                    ?: pageProps.optJSONObject("rastreo")
                    ?: pageProps.optJSONObject("envio")
                    ?: continue

                val status = tracking.optString("estado")
                    .ifBlank { tracking.optString("estado_actual").ifBlank { null } }
                val eventsArr = tracking.optJSONArray("novedades")
                    ?: tracking.optJSONArray("historial")
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

    // ─── Parseo de estado actual ───────────────────────────────────────────────

    private fun parseStatus(doc: Document): String? {
        // Intentar selectores específicos del portal WPS y del nuevo diseño React/Next.js
        val statusSelectors = listOf(
            // Nuevo diseño (React/Next.js) — tarjeta de estado actual visible en screenshot
            "[class*=estado-actual]",
            "[class*=estadoActual]",
            "[class*=estado-envio]",
            "[class*=estadoEnvio]",
            "[class*=estado-guia]",
            "[class*=tracking-state]",
            "[class*=trackingState]",
            // Portal WPS portlet content (diseño legacy)
            ".portlet-body .estado",
            ".portlet-body .estado-actual",
            "#resultado-rastreo .estado",
            // Bootstrap / genérico
            ".tracking-status",
            "[class*=tracking-status]",
            ".alert-success", ".alert-info", ".alert",
            // Último estado en tabla de eventos (primera fila de datos)
            "table tr:nth-child(2) td:nth-child(2)",
            "table tr:first-child td:nth-child(2)",
        )

        for (selector in statusSelectors) {
            val el = doc.selectFirst(selector)
            val text = el?.text()?.trim()?.takeIf { it.isNotBlank() && it.length > 3 }
            if (text != null) {
                android.util.Log.d(TAG, "Estado con '$selector': $text")
                return text
            }
        }
        return null
    }

    // ─── Parseo de tabla de eventos ───────────────────────────────────────────

    private fun parseEvents(doc: Document): List<CarrierScraperEvent> {
        // Buscar tablas que parezcan de tracking (al menos 3 columnas: fecha, descripción, ciudad)
        val tableSelectors = listOf(
            // Nuevo diseño (React/Next.js) — historial de eventos visible en screenshot
            "table[class*=historial]",
            "[class*=historial] table",
            "table[class*=rastreo]",
            "table[class*=tracking]",
            "table[class*=detalle]",
            // Portal WPS legacy
            ".portlet-body table",
            "#resultado-rastreo table",
            "[class*=resultado] table",
            // Fallback: cualquier tabla
            "table",
        )

        for (selector in tableSelectors) {
            val table = doc.selectFirst(selector) ?: continue
            val rows = table.select("tr").drop(1) // omitir encabezado
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

            if (events.isNotEmpty()) {
                android.util.Log.d(TAG, "Eventos con '$selector': ${events.size}")
                return events
            }
        }

        // Fallback: buscar lista de novedades/historial (div/li)
        val listSelectors = listOf(
            "[class*=historial]", "[class*=novedad]", "[class*=evento]", "[class*=detalle-rastreo]"
        )
        for (selector in listSelectors) {
            val items = doc.select(selector)
            if (items.isEmpty()) continue
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
