package com.brk718.tracker.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
 * Estrategia (en orden):
 *  1. REST API vía WordPress AJAX — muchos sitios WordPress colombianos exponen
 *     un endpoint admin-ajax.php para rastreo.
 *  2. Buscar JSON embebido en script tags (__NEXT_DATA__, application/json).
 *  3. Parsear HTML con Jsoup, buscando dentro del área de contenido principal
 *     (main / .entry-content / article) para evitar texto de navegación.
 *
 * NOTA: Si Coordinadora cambia el layout, ajustar los selectores en [parseHtmlStatus]
 * y [parseHtmlEvents]. El log de HTML (ver abajo) muestra los primeros 5000 chars
 * del cuerpo cuando no se encuentran eventos — muy útil para re-ajustar selectores.
 */
@Singleton
class CoordinadoraScraper @Inject constructor(
    @ApplicationContext private val context: Context
) : ColombianCarrierScraper {

    companion object {
        private const val TAG = "CoordinadoraScraper"
        private const val TRACKING_URL =
            "https://coordinadora.com/rastreo/rastreo-de-guia/detalle-de-rastreo-de-guia/"
        private const val AJAX_URL =
            "https://coordinadora.com/wp-admin/admin-ajax.php"
        private const val TIMEOUT_S = 15L
        private const val DEBUG_HTML_MAX_CHARS = 5_000

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Mobile Safari/537.36"

        /**
         * Palabras que indican texto de navegación/sección del sitio, NO un estado
         * de rastreo real. Si el texto encontrado por un selector coincide con
         * alguna de estas entradas (ignore case), se descarta y se prueba el siguiente.
         */
        private val NAV_TEXT_BLACKLIST = setOf(
            "sobre coordinadora",
            "coordinadora",
            "servicios en línea",
            "servicios en linea",
            "contáctenos",
            "contactenos",
            "inicio",
            "trabaja con nosotros",
            "nuestras marcas",
            "nosotros",
            "clientes",
            "proveedores",
            "tracking",
            "rastrear",
            "rastreo",
            "detalle de rastreo",
        )
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
                // ── Intento 1: WordPress AJAX endpoint ────────────────────────────
                val ajaxResult = tryWordPressAjax(trackingNumber)
                if (ajaxResult != null) return@withContext ajaxResult

                // ── Intento 2 + 3: HTML scraping ──────────────────────────────────
                val url = "$TRACKING_URL?guia=$trackingNumber"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "es-CO,es;q=0.9,en;q=0.8")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    android.util.Log.w(TAG, "HTTP ${response.code} para $trackingNumber")
                    return@withContext CarrierScraperResult(null, emptyList(),
                        "HTTP ${response.code}")
                }

                val html = response.body?.string()
                response.close()
                if (html == null) {
                    return@withContext CarrierScraperResult(null, emptyList(), "Respuesta vacía")
                }

                val doc = Jsoup.parse(html)

                // Intento 2: JSON embebido (React / Next.js)
                val jsonResult = parseJsonScript(doc, trackingNumber)
                if (jsonResult != null) return@withContext jsonResult

                // Intento 3: HTML con Jsoup
                val status = parseHtmlStatus(doc)
                val events = parseHtmlEvents(doc)

                if (status == null && events.isEmpty()) {
                    android.util.Log.d(TAG,
                        "Sin datos para $trackingNumber — HTML (${html.length} chars):\n" +
                        html.take(DEBUG_HTML_MAX_CHARS))
                    return@withContext CarrierScraperResult(null, emptyList(),
                        "Sin datos (revisar selectores)")
                }

                // Si hay status pero 0 eventos, loguear HTML para depuración
                if (events.isEmpty()) {
                    android.util.Log.d(TAG,
                        "status OK pero 0 eventos para $trackingNumber — " +
                        "HTML (${html.length} chars):\n${html.take(DEBUG_HTML_MAX_CHARS)}")
                }

                android.util.Log.d(TAG,
                    "OK: status=$status, eventos=${events.size} para $trackingNumber")
                CarrierScraperResult(status, events)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error scraping $trackingNumber: ${e.message}", e)
                CarrierScraperResult(null, emptyList(), e.message)
            }
        }

    // ─── Intento 1: WordPress AJAX ────────────────────────────────────────────

    private suspend fun tryWordPressAjax(trackingNumber: String): CarrierScraperResult? =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("action", "cm_rastrear_guia")
                    .add("guia", trackingNumber)
                    .build()

                val request = Request.Builder()
                    .url(AJAX_URL)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/javascript, */*")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", "https://coordinadora.com")
                    .header("Referer", "$TRACKING_URL?guia=$trackingNumber")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val rawBody = response.body?.string()

                if (!response.isSuccessful || rawBody.isNullOrBlank()) {
                    android.util.Log.d(TAG, "AJAX ${response.code} — sin datos")
                    return@withContext null
                }

                // Intentar parsear como JSON
                return@withContext try {
                    val json = JSONObject(rawBody)
                    // Estructura esperada: { "success": true, "data": { "estado": "...", "novedades": [...] } }
                    if (!json.optBoolean("success")) return@withContext null
                    val data = json.optJSONObject("data") ?: return@withContext null

                    val status = data.optString("estado").takeIf { it.isNotBlank() }
                    val novedadesArr = data.optJSONArray("novedades")
                        ?: data.optJSONArray("eventos")
                        ?: data.optJSONArray("events")

                    val events = mutableListOf<CarrierScraperEvent>()
                    if (novedadesArr != null) {
                        for (i in 0 until novedadesArr.length()) {
                            val item = novedadesArr.optJSONObject(i) ?: continue
                            val timestamp = item.optString("fecha")
                                .ifBlank { item.optString("date").ifBlank { item.optString("hora") } }
                            val description = item.optString("descripcion")
                                .ifBlank { item.optString("description")
                                    .ifBlank { item.optString("novedad") } }
                            val location = item.optString("ciudad")
                                .ifBlank { item.optString("ubicacion")
                                    .ifBlank { item.optString("city") } }
                            if (description.isNotBlank()) {
                                events.add(CarrierScraperEvent(timestamp, description, location))
                            }
                        }
                    }

                    if (status != null || events.isNotEmpty()) {
                        android.util.Log.d(TAG,
                            "AJAX OK — status=$status, eventos=${events.size}")
                        CarrierScraperResult(status, events)
                    } else null
                } catch (_: Exception) {
                    // No era JSON o estructura distinta, descartamos
                    null
                }
            } catch (e: Exception) {
                android.util.Log.d(TAG, "AJAX falló: ${e.message}")
                null
            }
        }

    // ─── Intento 2: JSON en script tags ───────────────────────────────────────

    private fun parseJsonScript(doc: Document, trackingNumber: String): CarrierScraperResult? {
        val scriptSelectors = listOf(
            "script#__NEXT_DATA__",
            "script[type='application/json']",
            "script[type=\"application/json\"]",
        )

        for (selector in scriptSelectors) {
            val script = doc.selectFirst(selector) ?: continue
            try {
                val json = JSONObject(script.html())
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
                            .ifBlank { item.optString("description")
                                .ifBlank { item.optString("novedad") } }
                        val location = item.optString("ciudad")
                            .ifBlank { item.optString("city")
                                .ifBlank { item.optString("ubicacion") } }
                        if (description.isNotBlank()) {
                            events.add(CarrierScraperEvent(timestamp, description, location))
                        }
                    }
                }

                if (status != null || events.isNotEmpty()) {
                    android.util.Log.d(TAG,
                        "JSON encontrado — status=$status, eventos=${events.size}")
                    return CarrierScraperResult(status, events)
                }
            } catch (_: Exception) { }
        }
        return null
    }

    // ─── Intento 3: HTML con selectores ───────────────────────────────────────

    /**
     * Busca el estado/status del envío en el área de contenido principal,
     * excluyendo texto de navegación via [NAV_TEXT_BLACKLIST].
     */
    private fun parseHtmlStatus(doc: Document): String? {
        // Acotar la búsqueda al área de contenido para evitar nav/footer
        val contentArea = doc.selectFirst(
            "main, .entry-content, article, #content, .page-content, .site-content, .wpb_wrapper"
        ) ?: doc.body() ?: return null

        val selectors = listOf(
            "[class*=estado-guia]",
            "[class*=estado-envio]",
            "[class*=estado-actual]",
            "[class*=estado]",
            "[class*=status-tracking]",
            "[class*=tracking-status]",
            "[id*=estado]",
            "[id*=status]",
            ".alert-info",
            ".badge",
            "h2", "h3", "h4",
        )
        for (selector in selectors) {
            val text = contentArea.selectFirst(selector)?.text()?.trim()
                ?.takeIf { it.isNotBlank() && it.length in 4..120 }
                ?.takeIf { candidate ->
                    val lower = candidate.lowercase()
                    NAV_TEXT_BLACKLIST.none { lower == it || lower.contains(it) }
                }
            if (text != null) {
                android.util.Log.d(TAG, "Estado con '$selector': $text")
                return text
            }
        }
        return null
    }

    private fun parseHtmlEvents(doc: Document): List<CarrierScraperEvent> {
        val contentArea = doc.selectFirst(
            "main, .entry-content, article, #content, .page-content, .site-content, .wpb_wrapper"
        ) ?: doc.body() ?: return emptyList()

        val containerSelectors = listOf(
            "[class*=novedad]",
            "[class*=evento-rastreo]",
            "[class*=detalle-rastreo]",
            "[class*=tracking-event]",
            "[class*=rastreo-resultado]",
            "[class*=resultado-rastreo]",
            "table[class*=rastreo]",
            "table[class*=tracking]",
            ".card-body",
            "table",
        )

        for (selector in containerSelectors) {
            val container = contentArea.selectFirst(selector) ?: continue

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
