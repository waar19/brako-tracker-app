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
 * La página de rastreo carga los datos de forma **dinámica con JavaScript**:
 * el HTML inicial incluye `<div class="rgc_widget" data-csrf="<JWT>">` y el JS
 * del plugin `coordinadora-rastreo` (namespace `rgc/v1`) usa ese JWT para llamar a
 * `wp-json/rgc/v1/detail_tracking?remission_code=<guia>` con `Authorization: Bearer`.
 *
 * Estrategia (en orden):
 *  0. [tryRestWithCsrf] — Extrae el JWT de `data-csrf` con Jsoup y llama al REST API
 *     con `Authorization: Bearer`. Es el mismo mecanismo que usa el JS de la página.
 *  1. [parseJsonScript] — Busca JSON embebido en script tags (defensivo, no aplica
 *     en WP puro pero deja el fallback por si cambia la arquitectura).
 *  2. [parseHtmlStatus] / [parseHtmlEvents] — Último recurso con selectores HTML.
 *
 * NOTA: El JWT tiene una expiración de ~30 s. Como lo extraemos del mismo GET de
 * página y lo usamos inmediatamente, no hay problema de caducidad.
 */
@Singleton
class CoordinadoraScraper @Inject constructor(
    @ApplicationContext private val context: Context
) : ColombianCarrierScraper {

    companion object {
        private const val TAG = "CoordinadoraScraper"
        private const val TRACKING_URL =
            "https://coordinadora.com/rastreo/rastreo-de-guia/detalle-de-rastreo-de-guia/"
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

        private const val REST_API_URL =
            "https://coordinadora.com/wp-json/rgc/v1/detail_tracking"

        /**
         * Selector CSS para el widget de rastreo del plugin rgc.
         * El plugin embebe un JWT de corta duración (30s) en el atributo [data-csrf]
         * que usa su propio JS para llamar al REST API. Lo extraemos y lo reutilizamos.
         */
        private const val CSRF_SELECTOR = ".rgc_widget[data-csrf], [data-csrf]"
    }

    /**
     * Devuelve true si [candidate] es texto de navegación del sitio, no un estado real.
     * Usa igualdad exacta (no contains) para evitar rechazar estados válidos que
     * contengan la palabra "coordinadora" (p.ej. "A recibir por Coordinadora").
     */
    private fun isNavText(candidate: String): Boolean {
        val lower = candidate.lowercase()
        return NAV_TEXT_BLACKLIST.any { lower == it }
    }

    /** Cliente HTTP único — sin CookieJar para no enviar cookies de sesión WP. */
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun getTracking(trackingNumber: String): CarrierScraperResult =
        withContext(Dispatchers.IO) {
            try {
                // ── GET página → HTML estático con data-csrf + fallback content ─────────
                val pageUrl = "$TRACKING_URL?guia=$trackingNumber"
                val pageResponse = client.newCall(
                    Request.Builder()
                        .url(pageUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "es-CO,es;q=0.9,en;q=0.8")
                        .build()
                ).execute()

                if (!pageResponse.isSuccessful) {
                    android.util.Log.w(TAG, "HTTP ${pageResponse.code} para $trackingNumber")
                    return@withContext CarrierScraperResult(null, emptyList(),
                        "HTTP ${pageResponse.code}")
                }

                val pageHtml = pageResponse.body?.string()
                pageResponse.close()
                if (pageHtml == null) {
                    return@withContext CarrierScraperResult(null, emptyList(), "Respuesta vacía")
                }

                // ── Parsear HTML una sola vez ─────────────────────────────────────────────
                val doc = Jsoup.parse(pageHtml)

                // Extraer el JWT del atributo data-csrf del widget rgc.
                // El plugin embebe este token de 30s para que su propio JS llame al REST API;
                // nosotros lo reutilizamos exactamente igual que hace el JS de la página.
                val csrfToken = doc.selectFirst(CSRF_SELECTOR)?.attr("data-csrf")
                if (!csrfToken.isNullOrBlank()) {
                    android.util.Log.d(TAG,
                        "data-csrf encontrado (${csrfToken.take(40)}…) para $trackingNumber")
                } else {
                    android.util.Log.w(TAG,
                        "data-csrf NO encontrado en HTML de $trackingNumber")
                }

                // ── Intento 0: REST con Bearer JWT (mecanismo oficial del plugin rgc) ──────
                if (!csrfToken.isNullOrBlank()) {
                    val restResult = tryRestWithCsrf(trackingNumber, pageUrl, csrfToken)
                    if (restResult != null) return@withContext restResult
                }

                // ── Intento 1: JSON embebido (Next.js / React — no aplica aquí, pero defensivo) ─
                val jsonResult = parseJsonScript(doc, trackingNumber)
                if (jsonResult != null) return@withContext jsonResult

                // ── Intento 2 (último recurso): HTML con Jsoup ────────────────────────────
                val status = parseHtmlStatus(doc)
                val events = parseHtmlEvents(doc)

                if (status == null && events.isEmpty()) {
                    android.util.Log.d(TAG,
                        "Sin datos para $trackingNumber — HTML (${pageHtml.length} chars):\n" +
                        pageHtml.take(DEBUG_HTML_MAX_CHARS))
                    return@withContext CarrierScraperResult(null, emptyList(),
                        "Sin datos (revisar selectores)")
                }

                if (events.isEmpty()) {
                    android.util.Log.d(TAG,
                        "status OK pero 0 eventos para $trackingNumber — " +
                        "HTML (${pageHtml.length} chars):\n${pageHtml.take(DEBUG_HTML_MAX_CHARS)}")
                }

                android.util.Log.d(TAG,
                    "OK: status=$status, eventos=${events.size} para $trackingNumber")
                CarrierScraperResult(status, events)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error scraping $trackingNumber: ${e.message}", e)
                CarrierScraperResult(null, emptyList(), e.message)
            }
        }

    // ─── Intento 0: REST con Bearer JWT (data-csrf del plugin rgc) ───────────────

    /**
     * El plugin `coordinadora-rastreo` (namespace rgc/v1) embebe un JWT de corta duración
     * (~30 s) en el atributo [data-csrf] del elemento `.rgc_widget`.
     * Su propio JS lo lee y llama a este mismo endpoint con `Authorization: Bearer <token>`.
     * Nosotros hacemos exactamente lo mismo: extrajimos el token del HTML y lo usamos aquí.
     *
     * Respuesta JSON esperada:
     * {
     *   "tracking_number": "58048123030",
     *   "current_state_text": "A RECIBIR POR COORDINADORA",
     *   "states":  [ { "date": "2026-02-23 09:50:31", "description": "...", "active": true } ],
     *   "history": [ { "date": "...", "description": "...", "city": "..." }, … ]
     * }
     */
    private suspend fun tryRestWithCsrf(
        trackingNumber: String,
        pageUrl: String,
        csrfToken: String,
    ): CarrierScraperResult? = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(
                Request.Builder()
                    .url("$REST_API_URL?remission_code=$trackingNumber")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $csrfToken")
                    .header("Referer", pageUrl)
                    .build()
            ).execute()
            val body = resp.body?.string()
            android.util.Log.d(TAG,
                "REST-csrf: HTTP ${resp.code} para $trackingNumber — body=${body?.take(400)}")
            if (resp.isSuccessful && !body.isNullOrBlank()) parseRestTracking(body, trackingNumber)
            else null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "REST-csrf falló para $trackingNumber: ${e.message}")
            null
        }
    }

    /**
     * Parsea la respuesta JSON del endpoint rgc/v1/detail_tracking.
     *
     * Estructura real confirmada:
     * {
     *   "tracking_number":    "58048123030",
     *   "current_state_text": "A RECIBIR POR COORDINADORA",
     *   "states":  [ { "code":"1", "date":"2026-02-23 09:50:31",
     *                  "description":"A RECIBIR POR COORDINADORA", "active":true } ],
     *   "history": [ { "date":"2026-02-20 10:00:00", "description":"Guia generada",
     *                  "city":"Bogotá" }, … ]
     * }
     *
     * - [current_state_text]  → status principal
     * - [history]             → eventos históricos (más antiguo → más reciente)
     * - [states]              → estado(s) actual(es); se usa si history está vacío
     */
    private fun parseRestTracking(json: String, trackingNumber: String): CarrierScraperResult? {
        return try {
            val root = JSONObject(json)
            android.util.Log.d(TAG, "REST JSON keys: ${root.keys().asSequence().toList()}")

            // Estado actual
            val status = root.optString("current_state_text")
                .ifBlank { root.optString("estado") }
                .ifBlank { root.optString("state") }
                .takeIf { it.isNotBlank() && !isNavText(it) }

            val events = mutableListOf<CarrierScraperEvent>()

            // Historial de eventos (preferido)
            val historyArr = root.optJSONArray("history")
            if (historyArr != null && historyArr.length() > 0) {
                for (i in 0 until historyArr.length()) {
                    val item = historyArr.optJSONObject(i) ?: continue
                    val ts   = item.optString("date").ifBlank { item.optString("fecha") }
                    val desc = item.optString("description")
                        .ifBlank { item.optString("descripcion").ifBlank { item.optString("novedad") } }
                    val loc  = item.optString("city").ifBlank { item.optString("ciudad") }
                    if (desc.isNotBlank()) events.add(CarrierScraperEvent(ts, desc, loc))
                }
            }

            // Si no hay historial, usar states (al menos tiene el estado actual como evento)
            if (events.isEmpty()) {
                val statesArr = root.optJSONArray("states")
                if (statesArr != null) {
                    for (i in 0 until statesArr.length()) {
                        val item = statesArr.optJSONObject(i) ?: continue
                        val ts   = item.optString("date").ifBlank { item.optString("fecha") }
                        val desc = item.optString("description")
                            .ifBlank { item.optString("descripcion") }
                        val loc  = item.optString("city").ifBlank { item.optString("ciudad") }
                        if (desc.isNotBlank()) events.add(CarrierScraperEvent(ts, desc, loc))
                    }
                }
            }

            if (status == null && events.isEmpty()) {
                android.util.Log.w(TAG, "REST: JSON sin status ni eventos para $trackingNumber")
                null
            } else {
                android.util.Log.d(TAG, "REST OK: status=$status, eventos=${events.size}")
                CarrierScraperResult(status, events)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "REST parseRestTracking error: ${e.message}")
            null
        }
    }

    // ─── Intento 1: JSON en script tags (defensivo, no aplica en WP puro) ────────

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

                val status = tracking.optString("estado")
                    .takeIf { it.isNotBlank() && !isNavText(it) }
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
                ?.takeIf { !isNavText(it) }
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
