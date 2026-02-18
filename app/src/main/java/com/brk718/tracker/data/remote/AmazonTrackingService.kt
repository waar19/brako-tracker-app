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
class AmazonTrackingService @Inject constructor() {

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
        val location: String
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
    suspend fun getTracking(trackingNumber: String): AmazonTrackingResult = withContext(Dispatchers.IO) {
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
