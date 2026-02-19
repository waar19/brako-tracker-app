package com.brk718.tracker.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AmazonScraper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "AmazonScraper"
        private const val SCRAPE_TIMEOUT_MS = 35_000L
        private const val JS_DELAY_MS = 6000L // Amazon usa React, necesita tiempo para renderizar
        // User-Agent mobile — debe coincidir con el que usó el WebView al hacer login
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    data class ScrapedEvent(
        val date: String?,
        val message: String,
        val location: String?
    )

    data class ScrapedInfo(
        val status: String,
        val arrivalDate: String?,
        val location: String?,
        val progress: Int?,
        val events: List<ScrapedEvent> = emptyList()
    )

    // Scripts separados para evitar problemas de escape al empaquetar HTML en JSON
    private val debugScript = """
        (function() {
            return JSON.stringify({
                title: document.title || '',
                url: window.location.href || '',
                len: document.body ? document.body.innerHTML.length : 0
            });
        })();
    """.trimIndent()

    // Devuelve el innerHTML del body directamente como string (sin envolver en JSON)
    private val htmlScript = "document.body ? document.body.innerHTML : ''"

    /**
     * Parsea el HTML de Amazon con Jsoup usando los selectores exactos de la página mobile.
     *
     * Estructura confirmada del HTML mobile de Amazon (order-details):
     * - Status principal:  h3.od-status-message  (texto como "Llega el lunes")
     * - Status secundario: div.od-status-message  (texto como "En camino")
     * - Contenedor eventos: #od-tracking-sheet-content-0  (.od-tracking-events-bottomsheet-inner)
     * - Fila de fecha:      .a-row.a-spacing-medium  con span.a-size-base-plus.a-color-base
     * - Fila de evento:     .a-row.a-spacing-medium  con .od-vertical-line-wrapper (hora)
     *                       y .od-tracking-event-description-column (descripción + ubicación)
     */
    private fun parseHtmlWithJsoup(html: String): ScrapedInfo {
        val doc = Jsoup.parse(html)

        // ===== STATUS PRINCIPAL =====
        // Fuente 1: h3.od-status-message — el título principal (ej. "Llega el lunes")
        var status = doc.selectFirst("h3.od-status-message")?.text()?.trim() ?: ""

        // Fuente 2: div.od-status-message — estado secundario (ej. "En camino")
        if (status.isEmpty()) {
            status = doc.selectFirst("div.od-status-message")?.text()?.trim() ?: ""
        }

        // Fuente 3: cualquier elemento con clase od-status-message
        if (status.isEmpty()) {
            status = doc.selectFirst(".od-status-message")?.text()?.trim() ?: ""
        }

        if (status.isEmpty()) status = "En seguimiento"

        Log.d(TAG, "Status extraído: $status")

        // ===== FECHA DE ENTREGA ESPERADA =====
        // El status h3 ya incluye la fecha ("Llega el lunes"), la usamos como arrivalDate
        // si contiene un día de la semana
        val dayPattern = Regex(
            "(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo|" +
            "monday|tuesday|wednesday|thursday|friday|saturday|sunday)",
            RegexOption.IGNORE_CASE
        )
        var arrivalDate: String? = if (dayPattern.containsMatchIn(status)) status else null

        // Fallback: buscar en elementos que tengan "promesa" u "estimated"
        if (arrivalDate.isNullOrBlank()) {
            arrivalDate = doc.select(
                "[class*=promise], [class*=arrival], [class*=expected], " +
                "[class*=delivery-date], [class*=deliveryDate], [class*=od-delivery]"
            ).firstOrNull()?.text()?.trim()
        }

        Log.d(TAG, "Fecha de entrega: $arrivalDate")

        // ===== EVENTOS DE TRACKING =====
        val events = mutableListOf<ScrapedEvent>()

        // Contenedor principal de eventos. Amazon duplica este contenedor en el DOM:
        // uno visible (#od-tracking-sheet-content-0) y otro oculto (aok-hidden).
        // Usamos selectFirst para tomar SOLO el primero y evitar duplicados.
        val trackingContainer = doc.selectFirst(
            "#od-tracking-sheet-content-0, .od-tracking-events-bottomsheet-inner"
        )

        if (trackingContainer != null) {
            Log.d(TAG, "Contenedor de eventos encontrado: #od-tracking-sheet-content-0")

            // Estructura real (confirmada del HTML capturado del dispositivo):
            //
            // #od-tracking-sheet-content-0
            //   └─ .a-container
            //        └─ .a-scroller
            //             └─ .a-row  (un grupo por día)
            //                  ├─ .a-row.a-spacing-medium  ← fila de fecha
            //                  │    └─ span "miércoles, 18 de febrero"
            //                  └─ .a-row.a-spacing-medium  ← fila de evento
            //                       ├─ .a-column.od-vertical-line-wrapper
            //                       │    └─ span "5:14 PM"
            //                       └─ .a-column.od-tracking-event-description-column
            //                            └─ .a-row
            //                                 ├─ span "Paquete recibido..."
            //                                 └─ span.a-text-italic "Miami, FLORIDA US"
            //
            // Estrategia: seleccionar todas las .od-tracking-event-description-column dentro
            // del contenedor. Para cada una:
            //   - la hora está en el .od-vertical-line-wrapper hermano (mismo padre .a-row.a-spacing-medium)
            //   - la fecha está en la primera .a-row.a-spacing-medium SIN od-vertical-line-wrapper
            //     dentro del mismo .a-row abuelo (el grupo de día)

            // Usar el contenedor no oculto si existe, o el primero disponible
            val activeContainer = doc.select("#od-tracking-sheet-content-0")
                .firstOrNull { !it.hasClass("aok-hidden") }
                ?: trackingContainer

            val descCols = activeContainer.select(".od-tracking-event-description-column")
            Log.d(TAG, "Columnas de descripción encontradas en contenedor: ${descCols.size}")

            for (descCol in descCols) {
                // Hora: hermano .od-vertical-line-wrapper en el mismo .a-row padre
                val eventRow = descCol.parent()   // .a-row.a-spacing-medium (fila de evento)
                val timeText = eventRow
                    ?.selectFirst(".od-vertical-line-wrapper span")
                    ?.text()?.trim() ?: ""

                // Mensaje: primer span no itálico dentro de la columna de descripción
                val spans = descCol.select("span")
                val msgText = spans.firstOrNull { !it.hasClass("a-text-italic") && it.text().length >= 3 }
                    ?.text()?.trim() ?: descCol.ownText().trim()

                // Ubicación: primer span itálico
                val locText = spans.firstOrNull { it.hasClass("a-text-italic") }
                    ?.text()?.trim()

                // Fecha: la fila hermana anterior del eventRow que NO tenga od-vertical-line-wrapper
                // (= la fila de fecha del grupo de día)
                val dayGroup = eventRow?.parent()  // .a-row (grupo de día)
                var dateText = ""
                if (dayGroup != null) {
                    for (sibling in dayGroup.children()) {
                        if (sibling == eventRow) break
                        // Es fila de fecha si no tiene od-vertical-line-wrapper
                        if (sibling.selectFirst(".od-vertical-line-wrapper") == null) {
                            val candidate = sibling.selectFirst("span.a-size-base-plus")
                                ?.text()?.trim() ?: sibling.selectFirst("span")?.text()?.trim() ?: ""
                            if (candidate.isNotBlank()) dateText = candidate
                        }
                    }
                }

                if (msgText.length >= 3) {
                    val fullDate = when {
                        dateText.isNotBlank() && timeText.isNotBlank() -> "$dateText $timeText"
                        timeText.isNotBlank() -> timeText
                        else -> dateText
                    }
                    events.add(ScrapedEvent(
                        date = fullDate.ifBlank { null },
                        message = msgText,
                        location = locText?.ifBlank { null }
                    ))
                    Log.d(TAG, "Evento: [$fullDate] $msgText @ ${locText ?: "sin ubicación"}")
                }
            }
        } else {
            Log.w(TAG, "Contenedor #od-tracking-sheet-content-0 NO encontrado en el HTML")
        }

        // Estrategia de respaldo: buscar en todo el DOM si el contenedor principal no tiene eventos
        if (events.isEmpty()) {
            Log.d(TAG, "Estrategia 2: buscar .od-tracking-event-description-column en todo el DOM")
            val descCols = doc.select(".od-tracking-event-description-column")
            for (descCol in descCols) {
                val spans = descCol.select("span")
                val msgText = spans.firstOrNull { !it.hasClass("a-text-italic") && it.text().length >= 3 }
                    ?.text()?.trim() ?: descCol.ownText().trim()
                val locText = spans.firstOrNull { it.hasClass("a-text-italic") }
                    ?.text()?.trim()
                val eventRow = descCol.parent()
                val timeText = eventRow?.selectFirst(".od-vertical-line-wrapper span")
                    ?.text()?.trim() ?: ""
                if (msgText.length >= 3) {
                    events.add(ScrapedEvent(
                        date = timeText.ifBlank { null },
                        message = msgText,
                        location = locText?.ifBlank { null }
                    ))
                    Log.d(TAG, "Evento (est2): [$timeText] $msgText @ ${locText ?: "sin ubicación"}")
                }
            }
        }

        Log.d(TAG, "Total eventos extraídos (antes de dedup): ${events.size}")

        // ===== DEDUPLICACIÓN =====
        // Amazon puede tener el mismo contenedor de tracking duplicado en el DOM
        // (ej. una copia visible y otra oculta). Eliminamos duplicados exactos
        // comparando mensaje + hora + ubicación.
        val seen = mutableSetOf<String>()
        val uniqueEvents = events.filter { ev ->
            val key = "${ev.message.trim().lowercase()}|${ev.date?.trim() ?: ""}|${ev.location?.trim()?.lowercase() ?: ""}"
            seen.add(key) // add() devuelve true si fue añadido (nuevo), false si ya existía
        }
        Log.d(TAG, "Total eventos únicos: ${uniqueEvents.size}")

        // ===== LOCATION PARA MAPA =====
        val location = uniqueEvents.firstOrNull { !it.location.isNullOrBlank() }?.location

        return ScrapedInfo(
            status = status,
            arrivalDate = arrivalDate?.ifBlank { null },
            location = location,
            progress = null,
            events = uniqueEvents
        )
    }

    /**
     * Punto de entrada principal. Scrape la página de order-details de Amazon.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun scrapeOrder(orderId: String, cookies: String): ScrapedInfo {
        if (cookies.isBlank()) {
            throw Exception("No cookies available")
        }

        val url = "https://www.amazon.com/gp/your-account/order-details?orderID=$orderId"

        val result = withTimeoutOrNull(SCRAPE_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                scrapeWithWebView(url, cookies)
            }
        }

        return result ?: ScrapedInfo(
            status = "No disponible",
            arrivalDate = null,
            location = null,
            progress = null,
            events = emptyList()
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun scrapeWithWebView(url: String, cookies: String): ScrapedInfo {
        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var webView: WebView? = null
            var isResumed = false

            fun cleanup() {
                handler.post {
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                    } catch (e: Exception) {
                        Log.w(TAG, "Cleanup error: ${e.message}")
                    }
                    webView = null
                }
            }

            fun resumeOnce(info: ScrapedInfo) {
                if (!isResumed) {
                    isResumed = true
                    cleanup()
                    continuation.resume(info)
                }
            }

            try {
                // Inyectar cookies
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                for (part in cookies.split(";")) {
                    val trimmed = part.trim()
                    if (trimmed.isNotEmpty()) {
                        cookieManager.setCookie("https://www.amazon.com", trimmed)
                    }
                }
                cookieManager.flush()

                val wv = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                    visibility = View.GONE
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = USER_AGENT
                    settings.blockNetworkImage = true
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                }
                webView = wv

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        val currentUrl = pageUrl ?: ""
                        Log.d(TAG, "Page loaded: $currentUrl")

                        // Redirigido al login → sesión expirada
                        if (currentUrl.contains("ap/signin", ignoreCase = true) ||
                            currentUrl.contains("sign-in", ignoreCase = true)) {
                            Log.w(TAG, "Redirected to sign-in")
                            resumeOnce(ScrapedInfo(
                                status = "Sign-In required",
                                arrivalDate = null, location = null, progress = null
                            ))
                            return
                        }

                        // La página order-details ya contiene el modal de tracking en el DOM
                        // (como a-popover-preload, oculto pero parseable con Jsoup).
                        // Esperamos a que React renderice y extraemos directo.
                        if (currentUrl.contains("order-details", ignoreCase = true)) {
                            handler.postDelayed({
                                extractAndParse(wv) { info -> resumeOnce(info) }
                            }, JS_DELAY_MS)
                            return
                        }

                        // Si llegamos a cualquier otra página inesperada, también extraemos
                        Log.d(TAG, "URL inesperada, intentando extraer: $currentUrl")
                        handler.postDelayed({
                            extractAndParse(wv) { info -> resumeOnce(info) }
                        }, JS_DELAY_MS)
                    }

                    override fun onReceivedError(
                        view: WebView?, errorCode: Int,
                        description: String?, failingUrl: String?
                    ) {
                        Log.e(TAG, "WebView error: $errorCode - $description para $failingUrl")
                        if (failingUrl == url) {
                            resumeOnce(ScrapedInfo(
                                status = "Error de conexión",
                                arrivalDate = null, location = null, progress = null
                            ))
                        }
                    }
                }

                // Timeout de seguridad: extraer lo que haya si tardamos demasiado
                handler.postDelayed({
                    if (!isResumed) {
                        Log.w(TAG, "Timeout alcanzado, extrayendo lo que hay")
                        extractAndParse(wv) { info -> resumeOnce(info) }
                    }
                }, SCRAPE_TIMEOUT_MS - 3000)

                Log.d(TAG, "Cargando: $url")
                wv.loadUrl(url)

            } catch (e: Exception) {
                Log.e(TAG, "Error configurando WebView: ${e.message}")
                resumeOnce(ScrapedInfo(
                    status = "Error: ${e.message}",
                    arrivalDate = null, location = null, progress = null
                ))
            }

            continuation.invokeOnCancellation { cleanup() }
        }
    }

    /**
     * Extrae el HTML del WebView en dos pasos:
     * 1. debugScript → obtiene título/URL/tamaño (JSON pequeño, sin riesgo de escape)
     * 2. htmlScript  → obtiene el innerHTML directamente como string JS
     *
     * Separar los dos evita corrupción del JSON al empaquetar HTML con comillas dentro.
     */
    private fun extractAndParse(webView: WebView, callback: (ScrapedInfo) -> Unit) {
        try {
            // Paso 1: info de debug (título, URL)
            webView.evaluateJavascript(debugScript) { debugResult ->
                try {
                    val debugJson = JSONObject(
                        debugResult?.trim()?.removeSurrounding("\"")
                            ?.replace("\\\"", "\"") ?: "{}"
                    )
                    val title = debugJson.optString("title", "")
                    val url = debugJson.optString("url", "")
                    val len = debugJson.optInt("len", 0)
                    Log.d(TAG, "Página: '$title' | URL: $url | HTML len: $len")

                    if (title.contains("Sign-In", ignoreCase = true) ||
                        title.contains("Iniciar sesión", ignoreCase = true) ||
                        url.contains("ap/signin", ignoreCase = true)) {
                        callback(ScrapedInfo("Sign-In required", null, null, null))
                        return@evaluateJavascript
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Debug parse error (no crítico): ${e.message}")
                }

                // Paso 2: obtener el HTML completo directamente
                try {
                    webView.evaluateJavascript(htmlScript) { rawHtml ->
                        try {
                            // evaluateJavascript devuelve el innerHTML como string JS escapado:
                            // - envuelto en comillas dobles externas
                            // - Chrome escapa < y > como \u003C y \u003E (seguridad XSS)
                            // - \n, \t, \r, \", \/, \\ como escapes JS estándar
                            // Hay que desenescapar TODO antes de pasarle a Jsoup,
                            // o los selectores CSS no encontrarán nada porque los tags
                            // serán literalmente "\u003Cdiv class=..." en vez de "<div class=..."
                            val html = rawHtml
                                ?.removeSurrounding("\"")   // quitar comillas externas del string JS
                                ?.replace("\\u003C", "<")   // < escapado por Chrome (crítico)
                                ?.replace("\\u003E", ">")   // > escapado por Chrome (crítico)
                                ?.replace("\\u003c", "<")   // variante minúscula
                                ?.replace("\\u003e", ">")   // variante minúscula
                                ?.replace("\\u0026", "&")   // & escapado
                                ?.replace("\\u0027", "'")   // ' escapado
                                ?.replace("\\n", "\n")
                                ?.replace("\\t", "\t")
                                ?.replace("\\r", "\r")
                                ?.replace("\\\"", "\"")
                                ?.replace("\\/", "/")
                                ?.replace("\\\\", "\\")
                                ?: ""

                            Log.d(TAG, "HTML extraído, longitud: ${html.length}")

                            // DEBUG: guardar HTML capturado al almacenamiento interno
                            try {
                                val file = java.io.File(context.filesDir, "amazon_debug.html")
                                file.writeText(html)
                                Log.d(TAG, "HTML guardado en: ${file.absolutePath}")
                            } catch (e: Exception) {
                                Log.w(TAG, "No se pudo guardar HTML debug: ${e.message}")
                            }
                            // DEBUG: loguear selectores clave para diagnosticar estructura real
                            try {
                                val testDoc = org.jsoup.Jsoup.parse(html)
                                Log.d(TAG, "DEBUG od-status-message (h3): '${testDoc.selectFirst("h3.od-status-message")?.text()}'")
                                Log.d(TAG, "DEBUG od-status-message (div): '${testDoc.selectFirst("div.od-status-message")?.text()}'")
                                Log.d(TAG, "DEBUG od-tracking-sheet-content-0: ${testDoc.selectFirst("#od-tracking-sheet-content-0") != null}")
                                Log.d(TAG, "DEBUG od-tracking-event-description-column count: ${testDoc.select(".od-tracking-event-description-column").size}")
                                Log.d(TAG, "DEBUG od-vertical-line-wrapper count: ${testDoc.select(".od-vertical-line-wrapper").size}")
                                val trackContainerLen = testDoc.selectFirst("#od-tracking-sheet-content-0")?.childrenSize() ?: 0
                                Log.d(TAG, "DEBUG od-tracking-sheet-content-0 children: $trackContainerLen")
                            } catch (e: Exception) {
                                Log.w(TAG, "DEBUG parse error: ${e.message}")
                            }

                            if (html.isBlank()) {
                                callback(ScrapedInfo("Sin contenido", null, null, null))
                                return@evaluateJavascript
                            }

                            val info = parseHtmlWithJsoup(html)
                            callback(info)

                        } catch (e: Exception) {
                            Log.e(TAG, "Error procesando HTML: ${e.message}")
                            callback(ScrapedInfo("Error al parsear HTML", null, null, null))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error obteniendo HTML: ${e.message}")
                    callback(ScrapedInfo("Error al ejecutar JS", null, null, null))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en extractAndParse: ${e.message}")
            callback(ScrapedInfo("Error al ejecutar JS", null, null, null))
        }
    }
}
