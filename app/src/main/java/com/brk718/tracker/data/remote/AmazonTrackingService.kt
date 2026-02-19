package com.brk718.tracker.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Servicio que consulta la API interna de Amazon Shipping para obtener
 * el estado de tracking de paquetes. Funciona con tracking IDs de Amazon
 * como TBA, AMZPSR, etc.
 */
@Singleton
class AmazonTrackingService @Inject constructor(
    private val sessionManager: com.brk718.tracker.data.local.AmazonSessionManager,
    private val scraper: com.brk718.tracker.data.remote.AmazonScraper,
    private val geocodingService: com.brk718.tracker.data.remote.GeocodingService
) {

    // Cliente HTTP propio (sin interceptores de AfterShip)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AmazonTracking"
        private const val BASE_URL = "https://track.amazon.com/api/tracker/"
    }

    data class AmazonTrackingResult(
        val status: String?,
        val expectedDelivery: String?,
        val events: List<AmazonTrackingEvent>,
        val error: String? = null
    )

    data class AmazonTrackingEvent(
        val timestamp: String,
        val description: String,
        val location: String,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    /**
     * Determina si un tracking number es de Amazon
     */
    fun isAmazonTracking(trackingNumber: String): Boolean {
        val upper = trackingNumber.uppercase()
        return upper.startsWith("TBA") ||
               upper.startsWith("AMZPSR") ||
               upper.startsWith("AMZ") ||
               upper.matches(Regex("^\\d{3}-\\d{7}-\\d{7}$")) // Order ID format
    }

    /**
     * Consulta el estado de un tracking de Amazon
     */
    /**
     * Consulta el estado de un tracking de Amazon.
     * Si es un tracking ID normal (TBA...), usa la API interna.
     * Si es un Order ID (111-...), intenta usar el scraper (requiere login).
     */
    suspend fun getTracking(trackingNumber: String): AmazonTrackingResult {
        // 1. Si es Order ID (111-...), usar Scraper (WebView-based)
        if (trackingNumber.matches(Regex("^\\d{3}-\\d{7}-\\d{7}$"))) {
            if (!sessionManager.isLoggedIn()) {
                return AmazonTrackingResult(
                    status = null, expectedDelivery = null, events = emptyList(), 
                    error = "LOGIN_REQUIRED"
                )
            }
            
            try {
                val cookies = sessionManager.getCookies() ?: throw Exception("No cookies")
                // scrapeOrder es suspend y maneja internamente el Main thread para WebView
                val info = scraper.scrapeOrder(trackingNumber, cookies)
                Log.d(TAG, "Scraped Info for $trackingNumber: Status=${info.status}, Loc=${info.location}, Events=${info.events.size}")
                
                // Mapear info scrapeada a resultado
                val events = mutableListOf<AmazonTrackingEvent>()
                
                // Si tenemos eventos scrapeados, usarlos
                if (info.events.isNotEmpty()) {
                    var latestLocationGeocoded = false
                    
                    for (scrapedEvent in info.events) {
                        var lat: Double? = null
                        var lon: Double? = null
                        
                        // Geocodificar solo el evento más reciente que tenga ubicación (para el mapa)
                        if (!latestLocationGeocoded && !scrapedEvent.location.isNullOrBlank()) {
                            val coords = geocodingService.getCoordinates(scrapedEvent.location)
                            lat = coords?.lat
                            lon = coords?.lon
                            latestLocationGeocoded = true
                        }
                        
                        events.add(AmazonTrackingEvent(
                            timestamp = scrapedEvent.date ?: "", // Usar fecha parseada
                            description = scrapedEvent.message,
                            location = scrapedEvent.location ?: "",
                            latitude = lat,
                            longitude = lon
                        ))
                    }
                } else {
                    // Fallback: Si no hay eventos (layout desconocido), crear uno sintético con el status global
                    if (info.location != null || info.status.isNotEmpty()) {
                        var lat: Double? = null
                        var lon: Double? = null
                        
                        if (!info.location.isNullOrBlank()) {
                            val coords = geocodingService.getCoordinates(info.location)
                            lat = coords?.lat
                            lon = coords?.lon
                        }
                        
                        events.add(AmazonTrackingEvent(
                            timestamp = info.arrivalDate ?: System.currentTimeMillis().toString(),
                            description = info.status,
                            location = info.location ?: "",
                            latitude = lat,
                            longitude = lon
                        ))
                    }
                }
                
                return AmazonTrackingResult(
                    status = info.status,
                    expectedDelivery = info.arrivalDate,
                    events = events
                )
            } catch (e: Exception) {
                // Si falla por auth, borrar sesión
                if (e.message?.contains("Sign-In") == true || e.message?.contains("auth") == true) {
                    sessionManager.clearSession()
                    return AmazonTrackingResult(
                        status = null, expectedDelivery = null, events = emptyList(), 
                        error = "LOGIN_REQUIRED"
                    )
                }
                
                return AmazonTrackingResult(
                    status = null, expectedDelivery = null, events = emptyList(),
                    error = "Error de conexión con Amazon: ${e.message}"
                )
            }
        }

        // 2. Si es tracking ID normal (TBA...), usar API
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL$trackingNumber")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext AmazonTrackingResult(
                    status = null, expectedDelivery = null, events = emptyList(), error = "Sin respuesta"
                )

                Log.d(TAG, "Response: ${body.take(500)}")
                parseResponse(body)
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                AmazonTrackingResult(
                    status = null, expectedDelivery = null, events = emptyList(),
                    error = e.message
                )
            }
        }
    }

    private fun parseResponse(body: String): AmazonTrackingResult {
        val json = JSONObject(body)

        // Parsear progressTracker (viene como string JSON anidado)
        val progressTrackerStr = json.optString("progressTracker", "{}")
        val progressTracker = JSONObject(progressTrackerStr)

        // Verificar errores
        val errors = progressTracker.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val errorMsg = errors.getJSONObject(0).optString("errorMessage", "Error desconocido")
            return AmazonTrackingResult(
                status = null, expectedDelivery = null, events = emptyList(),
                error = errorMsg
            )
        }

        // Extraer estado
        val summary = progressTracker.optJSONObject("summary")
        val status = summary?.optString("status")?.takeIf { it.isNotBlank() && it != "null" }

        // Fecha de entrega esperada
        val expectedDelivery = progressTracker.optString("expectedDeliveryDate")
            ?.takeIf { it.isNotBlank() && it != "null" }

        // Parsear historial de eventos
        val events = mutableListOf<AmazonTrackingEvent>()
        val eventHistory = json.optJSONArray("eventHistory")
        if (eventHistory != null) {
            for (i in 0 until eventHistory.length()) {
                val event = eventHistory.getJSONObject(i)
                val eventCode = event.optString("eventCode", "")
                val timestamp = event.optString("eventTime", event.optString("timestamp", ""))
                val location = event.optString("location",
                    event.optJSONObject("address")?.let { addr ->
                        listOfNotNull(
                            addr.optString("city").takeIf { it.isNotBlank() },
                            addr.optString("state").takeIf { it.isNotBlank() },
                            addr.optString("country").takeIf { it.isNotBlank() }
                        ).joinToString(", ")
                    } ?: ""
                )
                val description = event.optString("statusSummary",
                    event.optString("eventDescription", eventCode)
                )

                events.add(AmazonTrackingEvent(
                    timestamp = timestamp,
                    description = description,
                    location = location
                ))
            }
        }

        // Mapear estado a español
        val statusText = when {
            status == null -> if (events.isNotEmpty()) "En seguimiento" else null
            status.contains("delivered", ignoreCase = true) -> "Entregado"
            status.contains("out for delivery", ignoreCase = true) -> "En reparto"
            status.contains("in transit", ignoreCase = true) -> "En tránsito"
            status.contains("shipped", ignoreCase = true) -> "Enviado"
            status.contains("arrived", ignoreCase = true) -> "En instalación"
            status.contains("departed", ignoreCase = true) -> "En camino"
            else -> status
        }



        return AmazonTrackingResult(
            status = statusText,
            expectedDelivery = expectedDelivery,
            events = events
        )
    }
}
