package com.brk718.tracker.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Scraper para Interrapidísimo Colombia.
 *
 * La web usa una Angular SPA (www3.interrapidisimo.com/SiguetuEnvio) cuya URL de tracking
 * lleva la guía cifrada con AES-256-CBC + PBKDF2. La app Angular consulta internamente:
 *   POST https://www3.interrapidisimo.com/ApiServInter/api/Mensajeria/ObtenerRastreoGuias
 *
 * Estrategia: llamar directamente a la API JSON con un token temporal de autenticación,
 * evitando el cifrado de URL y el WebView.
 *
 * Flujo:
 *  1. GET token temporal → ApiLogin/api/Autenticacion/GenerarTokenTemporal
 *  2. POST con el número de guía → ApiServInter/api/Mensajeria/ObtenerRastreoGuias
 *  3. Parsear la respuesta JSON y mapear a TrackingResult
 *
 * Si la API directa falla, se usa el WebView como fallback cargando la URL cifrada.
 */
@Singleton
class InterrapidisimoScraper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geocodingService: GeocodingService
) {
    companion object {
        private const val TAG = "InterrapidisimoScraper"
        private const val BASE_URL        = "https://www3.interrapidisimo.com"
        private const val AUTH_URL        = "$BASE_URL/ApiLogin/api/Autenticacion/GenerarTokenTemporal"
        private const val TRACKING_URL    = "$BASE_URL/ApiServInter/api/Mensajeria/ObtenerRastreoGuias"
        private const val SPA_BASE_URL    = "$BASE_URL/SiguetuEnvio/shipment/"

        // Credenciales temporales que usa la app pública (sin login de usuario requerido)
        private const val APP_USER        = "AppPublica"
        private const val APP_PASSWORD    = "AppPublica2022*"

        private const val SCRAPE_TIMEOUT_MS = 30_000L
        private const val JS_DELAY_MS       = 5_000L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Determina si un número de guía es de Interrapidísimo (12 dígitos empezando por 24). */
        fun isInterrapidisimoTracking(trackingNumber: String): Boolean =
            trackingNumber.matches(Regex("24\\d{10}"))
    }

    data class TrackingResult(
        val status: String?,
        val origin: String?,
        val destination: String?,
        val events: List<TrackingEvent>,
        val error: String? = null
    )

    data class TrackingEvent(
        val timestamp: String,
        val description: String,
        val location: String,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Obtiene el estado de una guía de Interrapidísimo.
     * Primero intenta la API directa; si falla, usa el WebView como fallback.
     */
    suspend fun getTracking(trackingNumber: String): TrackingResult {
        // 1. Intentar API JSON directa
        val apiResult = tryDirectApi(trackingNumber)
        if (apiResult != null) return apiResult

        // 2. Fallback: WebView + cifrado
        Log.w(TAG, "API directa falló, usando WebView para $trackingNumber")
        return tryWebViewScrape(trackingNumber)
    }

    // ─────────────────────────────────────────────
    //  Estrategia 1: API JSON directa
    // ─────────────────────────────────────────────

    private suspend fun tryDirectApi(trackingNumber: String): TrackingResult? =
        withContext(Dispatchers.IO) {
            try {
                // Paso 1: obtener token temporal
                val token = fetchTemporaryToken() ?: run {
                    Log.w(TAG, "No se pudo obtener token temporal")
                    return@withContext null
                }
                Log.d(TAG, "Token obtenido: ${token.take(20)}...")

                // Paso 2: consultar rastreo
                val body = JSONObject().apply {
                    put("NumeroGuia", trackingNumber)
                }.toString().toRequestBody(JSON_MEDIA_TYPE)

                val request = Request.Builder()
                    .url(TRACKING_URL)
                    .post(body)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext null
                Log.d(TAG, "API response [${response.code}]: ${responseBody.take(500)}")

                if (!response.isSuccessful) return@withContext null

                parseApiResponse(responseBody)
            } catch (e: Exception) {
                Log.w(TAG, "tryDirectApi error: ${e.message}")
                null
            }
        }

    private suspend fun fetchTemporaryToken(): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("Usuario", APP_USER)
                put("Clave", APP_PASSWORD)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(AUTH_URL)
                .post(body)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null
            Log.d(TAG, "Auth response [${response.code}]: ${responseBody.take(200)}")

            if (!response.isSuccessful) return@withContext null

            // La respuesta puede ser el token directamente como string o dentro de un JSON
            val trimmed = responseBody.trim()
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                // Token como string JSON: "eyJ..."
                trimmed.removeSurrounding("\"")
            } else if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                json.optString("token")
                    ?: json.optString("Token")
                    ?: json.optString("access_token")
                    ?: json.optString("AccessToken")
            } else {
                trimmed.ifBlank { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchTemporaryToken error: ${e.message}")
            null
        }
    }

    private suspend fun parseApiResponse(json: String): TrackingResult? {
        return try {
            val root = JSONObject(json)

            // La respuesta puede tener éxito/error en distintos campos
            val success = root.optBoolean("Success", true)
            if (!success) {
                val msg = root.optString("Message", "Error desconocido")
                return TrackingResult(status = null, origin = null, destination = null,
                    events = emptyList(), error = msg)
            }

            // Buscar datos de rastreo en distintas estructuras posibles
            val dataObj = root.optJSONObject("Data")
                ?: root.optJSONObject("data")
                ?: root.optJSONObject("Result")
                ?: root

            // Estado principal
            val status = dataObj.optString("Estado", "")
                .ifBlank { dataObj.optString("estado", "") }
                .ifBlank { dataObj.optString("EstadoGuia", "") }
                .ifBlank { null }

            val origin = dataObj.optString("Origen", "")
                .ifBlank { dataObj.optString("origen", "") }
                .ifBlank { null }
                ?.replace("\\", " / ") // "BOGOTA\CUND\COL" → "BOGOTA / CUND / COL"

            val destination = dataObj.optString("Destino", "")
                .ifBlank { dataObj.optString("destino", "") }
                .ifBlank { null }
                ?.replace("\\", " / ")

            // Lista de eventos/novedades
            val eventsArray = dataObj.optJSONArray("Novedades")
                ?: dataObj.optJSONArray("novedades")
                ?: dataObj.optJSONArray("Eventos")
                ?: dataObj.optJSONArray("eventos")

            val geocodeCache = mutableMapOf<String, Pair<Double?, Double?>>()
            val events = mutableListOf<TrackingEvent>()

            if (eventsArray != null) {
                for (i in 0 until eventsArray.length()) {
                    val ev = eventsArray.getJSONObject(i)
                    val desc = ev.optString("Novedad", "")
                        .ifBlank { ev.optString("Descripcion", "") }
                        .ifBlank { ev.optString("descripcion", "") }
                        .ifBlank { ev.optString("Estado", "") }
                        .trim()
                    if (desc.isBlank()) continue

                    val fecha = ev.optString("Fecha", "")
                        .ifBlank { ev.optString("fecha", "") }
                    val hora = ev.optString("Hora", "")
                        .ifBlank { ev.optString("hora", "") }
                    val timestamp = when {
                        fecha.isNotBlank() && hora.isNotBlank() -> "$fecha $hora"
                        fecha.isNotBlank() -> fecha
                        else -> ""
                    }

                    val ciudad = ev.optString("Ciudad", "")
                        .ifBlank { ev.optString("ciudad", "") }
                        .replace("\\", " ")
                        .trim()

                    // Geocodificar si hay ciudad
                    var lat: Double? = null
                    var lon: Double? = null
                    if (ciudad.isNotBlank()) {
                        val cacheKey = ciudad.lowercase()
                        if (geocodeCache.containsKey(cacheKey)) {
                            val cached = geocodeCache[cacheKey]!!
                            lat = cached.first; lon = cached.second
                        } else {
                            val coords = geocodingService.getCoordinates("$ciudad, Colombia")
                            lat = coords?.lat; lon = coords?.lon
                            geocodeCache[cacheKey] = Pair(lat, lon)
                        }
                    }

                    events.add(TrackingEvent(
                        timestamp = timestamp,
                        description = desc,
                        location = ciudad,
                        latitude = lat,
                        longitude = lon
                    ))
                }
            }

            // Si no hay eventos pero sí estado, crear un evento sintético
            if (events.isEmpty() && !status.isNullOrBlank()) {
                val locationStr = origin ?: ""
                var lat: Double? = null; var lon: Double? = null
                if (locationStr.isNotBlank()) {
                    val coords = geocodingService.getCoordinates("$locationStr, Colombia")
                    lat = coords?.lat; lon = coords?.lon
                }
                events.add(TrackingEvent(
                    timestamp = "",
                    description = status,
                    location = locationStr,
                    latitude = lat,
                    longitude = lon
                ))
            }

            val statusEs = translateStatus(status ?: "En seguimiento")
            TrackingResult(
                status = statusEs,
                origin = origin,
                destination = destination,
                events = events
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseApiResponse error: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────
    //  Estrategia 2: WebView (fallback)
    // ─────────────────────────────────────────────

    private suspend fun tryWebViewScrape(trackingNumber: String): TrackingResult {
        val encryptedToken = encryptTrackingNumber(trackingNumber) ?: run {
            return TrackingResult(null, null, null, emptyList(),
                error = "No se pudo cifrar el número de guía")
        }
        val url = SPA_BASE_URL + encryptedToken
        Log.d(TAG, "WebView URL: $url")

        val result = withTimeoutOrNull(SCRAPE_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                scrapeWithWebView(url)
            }
        } ?: TrackingResult(null, null, null, emptyList(), error = "Timeout")

        return result
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun scrapeWithWebView(url: String): TrackingResult {
        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var webView: WebView? = null
            var isResumed = false

            fun cleanup() {
                handler.post {
                    try { webView?.stopLoading(); webView?.destroy() } catch (_: Exception) {}
                    webView = null
                }
            }

            fun resumeOnce(result: TrackingResult) {
                if (!isResumed) {
                    isResumed = true
                    cleanup()
                    continuation.resume(result)
                }
            }

            try {
                val wv = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                    visibility = View.GONE
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    settings.blockNetworkImage = true
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                }
                webView = wv

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        Log.d(TAG, "WebView loaded: $pageUrl")
                        handler.postDelayed({
                            extractAndParse(wv) { resumeOnce(it) }
                        }, JS_DELAY_MS)
                    }

                    override fun onReceivedError(
                        view: WebView?, errorCode: Int,
                        description: String?, failingUrl: String?
                    ) {
                        if (failingUrl == url) {
                            resumeOnce(TrackingResult(null, null, null, emptyList(),
                                error = "Error WebView: $description"))
                        }
                    }
                }

                handler.postDelayed({
                    if (!isResumed) {
                        Log.w(TAG, "WebView timeout — extrayendo lo que haya")
                        extractAndParse(wv) { resumeOnce(it) }
                    }
                }, SCRAPE_TIMEOUT_MS - 3000)

                wv.loadUrl(url)

            } catch (e: Exception) {
                Log.e(TAG, "WebView setup error: ${e.message}")
                resumeOnce(TrackingResult(null, null, null, emptyList(), error = e.message))
            }

            continuation.invokeOnCancellation { cleanup() }
        }
    }

    private fun extractAndParse(webView: WebView, callback: (TrackingResult) -> Unit) {
        val htmlScript = "document.body ? document.body.innerHTML : ''"
        webView.evaluateJavascript(htmlScript) { rawHtml ->
            try {
                val html = rawHtml
                    ?.removeSurrounding("\"")
                    ?.replace("\\u003C", "<")?.replace("\\u003E", ">")
                    ?.replace("\\u003c", "<")?.replace("\\u003e", ">")
                    ?.replace("\\u0026", "&")?.replace("\\u0027", "'")
                    ?.replace("\\n", "\n")?.replace("\\t", "\t")
                    ?.replace("\\r", "\r")?.replace("\\\"", "\"")
                    ?.replace("\\/", "/")?.replace("\\\\", "\\")
                    ?: ""

                Log.d(TAG, "WebView HTML length: ${html.length}")
                callback(parseHtml(html))
            } catch (e: Exception) {
                Log.e(TAG, "extractAndParse error: ${e.message}")
                callback(TrackingResult(null, null, null, emptyList(), error = e.message))
            }
        }
    }

    /**
     * Parsea el HTML de la Angular SPA de Interrapidísimo.
     *
     * Estructura observada (página de resultados):
     *   - Número de guía: <h2 class="orange-inter"> o título principal
     *   - Origen/Destino: elementos con texto "BOGOTA\CUND\COL"
     *   - Estado: elemento con clase "subtitle" o similar
     *   - Última actualización: texto "Última actualización: dd/MM/yyyy"
     *   - Eventos (modal "Ver más detalle"): tabla o lista de novedades
     */
    private fun parseHtml(html: String): TrackingResult {
        if (html.isBlank()) {
            return TrackingResult(null, null, null, emptyList(), error = "HTML vacío")
        }

        val doc = Jsoup.parse(html)

        // Detectar error del servidor
        val errorText = doc.selectFirst(".error-message, [class*=error]")?.text()?.trim()
        if (!errorText.isNullOrBlank() && errorText.contains("error", ignoreCase = true)) {
            return TrackingResult(null, null, null, emptyList(), error = errorText)
        }

        // Número de guía (para validar que cargó bien)
        val guideNumber = doc.selectFirst(".orange-inter, h2.orange-inter, [class*=guide-number]")
            ?.text()?.trim()
        Log.d(TAG, "Guía extraída del HTML: $guideNumber")

        // Origen y destino
        val allLabels = doc.select("span, p, div, h3, h4, h5")
        var origin: String? = null
        var destination: String? = null
        var statusText: String? = null

        // Buscar el bloque con "Origen" y "Destino"
        for (el in doc.select("[class*=orange-inter], .orange-inter")) {
            val text = el.text().trim()
            if (text.contains("\\") || text.matches(Regex("[A-Z]+\\\\[A-Z]+.*"))) {
                // Puede ser origen o destino — ver el label previo hermano
                val parent = el.parent()
                val label = parent?.selectFirst("span, p, label, h5, h6")?.text()?.trim()?.lowercase()
                when {
                    label?.contains("origen") == true  -> origin = text.replace("\\", " / ")
                    label?.contains("destino") == true -> destination = text.replace("\\", " / ")
                    origin == null                     -> origin = text.replace("\\", " / ")
                    destination == null                -> destination = text.replace("\\", " / ")
                }
            }
        }

        // Estado: dentro de la tarjeta principal
        val subtitleEl = doc.selectFirst(".subtitle, [class*=subtitle], [class*=estado-text]")
        statusText = subtitleEl?.text()?.trim()

        // Fallback: buscar texto en mayúsculas que parezca un estado
        if (statusText.isNullOrBlank()) {
            for (el in doc.select("span, p, div, h3, h4, h5")) {
                val t = el.text().trim()
                if (t.length in 5..60 &&
                    t == t.uppercase() &&
                    t.contains(" ") &&
                    !t.contains("\\") &&
                    !t.matches(Regex(".*\\d{2}/\\d{2}/\\d{4}.*"))) {
                    statusText = t
                    break
                }
            }
        }

        // Fecha de última actualización
        val lastUpdateEl = doc.getElementsContainingText("ltima actualizaci").firstOrNull()
        val lastUpdate = lastUpdateEl?.text()?.trim()
        Log.d(TAG, "Última actualización: $lastUpdate")

        // Eventos (tabla de novedades — dentro del modal o en la página)
        val events = mutableListOf<TrackingEvent>()
        val rows = doc.select("table tr, .novedad-row, [class*=novedad], [class*=event-row]")
        for (row in rows) {
            val cells = row.select("td, th, span, div")
            if (cells.size >= 2) {
                val desc = cells.getOrNull(0)?.text()?.trim() ?: continue
                val loc  = cells.getOrNull(1)?.text()?.trim() ?: ""
                val ts   = cells.getOrNull(2)?.text()?.trim() ?: ""
                if (desc.length >= 3) {
                    events.add(TrackingEvent(timestamp = ts, description = desc, location = loc))
                }
            }
        }

        // Si no hay eventos pero hay estado, crear evento sintético
        if (events.isEmpty() && !statusText.isNullOrBlank()) {
            val loc = origin ?: ""
            events.add(TrackingEvent(
                timestamp = lastUpdate ?: "",
                description = statusText,
                location = loc
            ))
        }

        val translatedStatus = translateStatus(statusText ?: "En seguimiento")
        Log.d(TAG, "Resultado WebView — status: $translatedStatus, origen: $origin, destino: $destination, eventos: ${events.size}")

        return TrackingResult(
            status = translatedStatus,
            origin = origin,
            destination = destination,
            events = events
        )
    }

    // ─────────────────────────────────────────────
    //  Cifrado AES-256-CBC (replicando el JS del sitio)
    // ─────────────────────────────────────────────

    /**
     * Cifra el número de guía con el mismo algoritmo que usa el sitio web de Interrapidísimo:
     *   - Salt: 256 bits aleatorios
     *   - Key:  PBKDF2-HMAC-SHA1, keySize=256 bits, iterations=1000
     *   - IV:   128 bits aleatorios
     *   - Cipher: AES/CBC/PKCS7
     *   - Output: Base64(salt + iv + ciphertext) con % → _ en la URL
     *
     * Usa javax.crypto (disponible en Android sin dependencias extra).
     */
    private fun encryptTrackingNumber(guide: String): String? {
        return try {
            val password = "Int3rr4p1d1s1m0Cl4S33ncr1pc10nPt2022"
            val saltBytes = java.security.SecureRandom().generateSeed(32) // 256 bits
            val ivBytes   = java.security.SecureRandom().generateSeed(16) // 128 bits

            // PBKDF2-HMAC-SHA1 para derivar la clave (igual que CryptoJS.PBKDF2 por defecto)
            val keySpec = javax.crypto.spec.PBEKeySpec(
                password.toCharArray(),
                saltBytes,
                1000,           // iterations
                256             // keyLen bits
            )
            val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = factory.generateSecret(keySpec).encoded

            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val ivSpec    = javax.crypto.spec.IvParameterSpec(ivBytes)

            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val cipherBytes = cipher.doFinal(guide.toByteArray(Charsets.UTF_8))

            // Concatenar: salt (32) + iv (16) + ciphertext
            val combined = saltBytes + ivBytes + cipherBytes
            val base64 = android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)

            // Reemplazar % por _ para la URL (como hace el JS con encodeURIComponent(...).replace(/%/g, "_"))
            java.net.URLEncoder.encode(base64, "UTF-8").replace("%", "_")
        } catch (e: Exception) {
            Log.e(TAG, "encryptTrackingNumber error: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────
    //  Traducción de estados
    // ─────────────────────────────────────────────

    private fun translateStatus(status: String): String {
        val s = status.uppercase()
        return when {
            s.contains("ENTREGAD") || s.contains("DELIVERED")       -> "Entregado"
            s.contains("CAMINO") || s.contains("TRANSIT")           -> "En tránsito"
            s.contains("REPARTO") || s.contains("OUT FOR DELIVERY")  -> "En reparto"
            s.contains("RECOGIDO") || s.contains("RECIBIDO")        -> "Recogido"
            s.contains("PENDIENTE")                                  -> "Pendiente"
            s.contains("ADMITIDO")                                   -> "Admitido"
            s.contains("NOVEDAD") || s.contains("INCIDENCIA")       -> "Novedad"
            s.contains("INTENTO") || s.contains("ATTEMPT")         -> "Intento fallido"
            s.contains("DEVUELTO") || s.contains("RETURN")         -> "Devuelto"
            s.contains("PRE ENVÍO") || s.contains("PRE ENVIO")     -> "Pre-envío"
            else -> status.lowercase()
                .replaceFirstChar { it.uppercaseChar() }
        }
    }
}
