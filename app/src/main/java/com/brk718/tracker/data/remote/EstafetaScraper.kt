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
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scraper para Estafeta México usando el formulario Liferay DXP de su sitio web.
 *
 * Flujo:
 *  1. GET https://www.estafeta.com/herramientas/rastreo → extraer:
 *     - Namespace del portlet Liferay (`p_p_id`)
 *     - Token de autenticación (`p_auth`)
 *  2. POST al mismo URL con los datos del formulario del portlet.
 *  3. Parsear HTML de respuesta con Jsoup (tabla de tracking) o JSON si aplica.
 *
 * Los valores exactos del namespace y la estructura de respuesta se descubren
 * con los logs "portletNs=... pAuth=..." y el HTML de respuesta en el primer run.
 * Si el scraper no puede extraer datos, cae a AfterShip como fallback automático
 * (Estafeta NO está en SCRAPER_ONLY_SLUGS).
 */
@Singleton
class EstafetaScraper @Inject constructor(
    @ApplicationContext private val context: Context
) : CarrierScraper {

    companion object {
        private const val TAG = "EstafetaScraper"
        private const val TRACKING_URL = "https://www.estafeta.com/herramientas/rastreo"
        private const val TIMEOUT_S = 20L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

        // Regex para descubrir el namespace del portlet Liferay en el HTML.
        // Liferay usa namespaces del tipo "_com_estafeta_portlet_rastreo_WAR_..."
        private val PORTLET_NS_REGEX = Regex("""p_p_id=([A-Za-z0-9_]+)""")
        private val P_AUTH_REGEX     = Regex("""p_auth=([A-Za-z0-9_\-]+)""")
        // También buscar en atributos de formulario
        private val FORM_ACTION_REGEX = Regex("""action="([^"]*rastreo[^"]*p_p_id=([^&"]+)[^"]*)"""")
    }

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
                // ── Paso 1: GET página para extraer namespace de portlet y p_auth ──
                val pageResponse = client.newCall(
                    Request.Builder()
                        .url(TRACKING_URL)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.9")
                        .header("Accept-Language", "es-MX,es;q=0.9")
                        .build()
                ).execute()
                val pageHtml = pageResponse.body?.string()
                    ?: return@withContext CarrierScraperResult(null, emptyList(), "Página vacía")

                // Extraer namespace del portlet y p_auth — varios enfoques para resiliencia
                val formActionMatch = FORM_ACTION_REGEX.find(pageHtml)
                val formAction = formActionMatch?.groupValues?.getOrNull(1)
                val portletNs  = formActionMatch?.groupValues?.getOrNull(2)
                    ?: PORTLET_NS_REGEX.find(pageHtml)?.groupValues?.getOrNull(1)
                val pAuth      = P_AUTH_REGEX.find(pageHtml)?.groupValues?.getOrNull(1)

                Log.d(TAG, "portletNs=$portletNs  pAuth=$pAuth  formAction=${formAction?.take(100)}")

                if (portletNs == null) {
                    // Liferay puede requerir sesión; log del HTML para diagnóstico
                    Log.w(TAG, "No se encontró portletNs — HTML (${pageHtml.length} chars):\n${pageHtml.take(2000)}")
                    return@withContext CarrierScraperResult(null, emptyList(), "portletNs no encontrado")
                }

                // ── Paso 2: POST formulario Liferay con la guía ────────────────────
                val postUrl = if (formAction != null && formAction.startsWith("http")) {
                    formAction
                } else {
                    // Construir URL del portlet manualmente
                    "$TRACKING_URL?p_p_id=${portletNs}&p_p_lifecycle=1&p_p_state=normal" +
                    "&p_p_mode=view&p_p_col_id=column-1&p_p_col_count=1" +
                    if (pAuth != null) "&p_auth=$pAuth" else ""
                }

                val bodyBuilder = FormBody.Builder()
                    .add("${portletNs}_wayBillType",    "1")
                    .add("${portletNs}_wayBillNumbers", trackingNumber)

                val postResponse = client.newCall(
                    Request.Builder()
                        .url(postUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.9")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Origin", "https://www.estafeta.com")
                        .header("Referer", TRACKING_URL)
                        .header("Accept-Language", "es-MX,es;q=0.9")
                        .post(bodyBuilder.build())
                        .build()
                ).execute()

                val responseBody = postResponse.body?.string()
                Log.d(TAG, "POST ${postResponse.code} — body=${responseBody?.take(1000)}")

                if (!postResponse.isSuccessful || responseBody.isNullOrBlank()) {
                    return@withContext CarrierScraperResult(null, emptyList(), "HTTP ${postResponse.code}")
                }

                parseHtmlResponse(responseBody, trackingNumber)

            } catch (e: Exception) {
                Log.e(TAG, "Error scraping $trackingNumber: ${e.message}", e)
                CarrierScraperResult(null, emptyList(), e.message)
            }
        }

    /**
     * Parsea la tabla de tracking del HTML de respuesta de Estafeta.
     * Los selectores exactos se ajustan según el HTML real logueado en el primer run.
     */
    private fun parseHtmlResponse(html: String, trackingNumber: String): CarrierScraperResult {
        return try {
            val doc = Jsoup.parse(html)

            // Intentar selectores comunes para tablas de tracking de Liferay
            val rows = doc.select("table.tracking-table tr, table.rastreo tr, .tracking-result tr, " +
                                  ".resultados-rastreo tr, .portlet-body table tr")

            Log.d(TAG, "Filas de tabla encontradas: ${rows.size}")

            val events = mutableListOf<CarrierScraperEvent>()
            var status: String? = null

            for ((i, row) in rows.withIndex()) {
                val cells = row.select("td")
                if (cells.size < 2) continue
                if (i == 0) Log.d(TAG, "Fila[0] cells: ${cells.map { it.text() }}")

                val ts   = cells.getOrNull(0)?.text()?.trim() ?: ""
                val desc = cells.getOrNull(1)?.text()?.trim() ?: ""
                val loc  = cells.getOrNull(2)?.text()?.trim() ?: ""

                if (desc.isNotBlank()) {
                    events.add(CarrierScraperEvent(ts, desc, loc))
                    if (i == 1) status = desc  // primer evento = estado actual
                }
            }

            // Intentar también un selector de estado directo
            if (status == null) {
                status = doc.select(".estado-actual, .current-status, .tracking-status, " +
                                   "[class*=status], [class*=estado]").firstOrNull()?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
            }

            if (status == null && events.isEmpty()) {
                Log.w(TAG, "Sin datos para $trackingNumber — HTML (${html.length} chars):\n${html.take(3000)}")
                CarrierScraperResult(null, emptyList(), "Sin datos (revisar selectores en Logcat)")
            } else {
                Log.d(TAG, "OK: status=$status, eventos=${events.size}")
                CarrierScraperResult(status, events)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseHtmlResponse error: ${e.message}")
            CarrierScraperResult(null, emptyList(), e.message)
        }
    }
}
