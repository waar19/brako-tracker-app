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
 * Scraper para TCC Colombia.
 *
 * URL pública: https://www.tcc.com.co/rastreo/?codigo={codigo}
 *
 * El sitio de TCC usa una página HTML tradicional con una tabla Bootstrap
 * que lista los eventos de rastreo.
 *
 * NOTA: Si TCC cambia el layout, ajustar los selectores en [parseStatus] y [parseEvents].
 */
@Singleton
class TccScraper @Inject constructor(
    @ApplicationContext private val context: Context
) : ColombianCarrierScraper {

    companion object {
        private const val TAG = "TccScraper"
        private const val BASE_URL = "https://www.tcc.com.co/rastreo/"
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
                val url = "$BASE_URL?codigo=$trackingNumber"
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

    private fun parseStatus(doc: Document): String? {
        val selectors = listOf(
            "[class*=estado-actual]",
            "[class*=estado-envio]",
            "[class*=estado]",
            ".tracking-state",
            "[class*=tracking-state]",
            ".alert-success",
            ".alert-info",
            ".label-tracking",
            // Primera celda de descripción en la tabla de eventos (evento más reciente)
            "table tr:nth-child(2) td:nth-child(2)",
            "table tr:nth-child(2) td:nth-child(3)",
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

    private fun parseEvents(doc: Document): List<CarrierScraperEvent> {
        // TCC usa Bootstrap — buscar primero tablas con clases Bootstrap
        val tableSelectors = listOf(
            "table.table",
            "table[class*=rastreo]",
            "table[class*=tracking]",
            ".rastreo table",
            ".tracking table",
            ".resultado table",
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

        // Fallback: buscar items de lista con "novedad", "evento"
        for (selector in listOf("[class*=novedad]", "[class*=evento]", "[class*=detalle]")) {
            val items = doc.select(selector)
            val events = items.mapNotNull { item ->
                val text = item.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CarrierScraperEvent("", text, "")
            }
            if (events.isNotEmpty()) return events
        }

        return emptyList()
    }
}
