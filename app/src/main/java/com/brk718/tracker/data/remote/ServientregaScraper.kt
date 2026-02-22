package com.brk718.tracker.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scraper para Servientrega Colombia.
 *
 * URL pública: https://www.servientrega.com/wps/portal/rastreo-envio?tracking={guia}
 * El portal IBM WebSphere Portal (WPS) renderiza el HTML server-side con los eventos
 * de rastreo en tablas.
 *
 * Estrategia:
 *  1. GET con User-Agent de navegador real para evitar bloqueos básicos
 *  2. Jsoup parsea el HTML buscando selectores conocidos del portal WPS
 *  3. Si ningún selector coincide, se logea el HTML truncado para depuración
 *
 * NOTA: Si Servientrega cambia el layout del portal, ajustar los selectores en
 * [parseStatus] y [parseEvents].
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

    // ─── Parseo de estado actual ───────────────────────────────────────────────

    private fun parseStatus(doc: Document): String? {
        // Intentar selectores específicos del portal WPS de Servientrega (en orden de probabilidad)
        val statusSelectors = listOf(
            // Portal WPS portlet content
            ".portlet-body .estado",
            ".portlet-body .estado-actual",
            "#resultado-rastreo .estado",
            "[class*=estado-actual]",
            "[class*=estado-envio]",
            // Bootstrap / genérico
            ".tracking-status",
            "[class*=tracking-status]",
            ".alert",
            // Último estado en una tabla de eventos (primera fila)
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
            "table[class*=rastreo]",
            "table[class*=tracking]",
            "table[class*=detalle]",
            ".portlet-body table",
            "#resultado-rastreo table",
            "[class*=resultado] table",
            // Fallback: cualquier tabla con ≥3 columnas
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

        // Fallback: buscar lista de novedades (div/li)
        val listSelectors = listOf(
            "[class*=novedad]", "[class*=evento]", "[class*=detalle-rastreo]"
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
