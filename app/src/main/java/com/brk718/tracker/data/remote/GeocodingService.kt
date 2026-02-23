package com.brk718.tracker.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeocodingService @Inject constructor() {

    private val client = OkHttpClient()

    data class GeoLocation(val lat: Double, val lon: Double)

    /**
     * Cache de sesión: persiste toda la vida del proceso (@Singleton).
     * Evita re-geocodificar las mismas ciudades en cada refresh.
     * Almacena null también (ciudades que Nominatim no conoce) para no reintentar.
     * No cachea en caso de excepción de red (error transitorio).
     */
    private val sessionCache = java.util.concurrent.ConcurrentHashMap<String, GeoLocation?>()

    suspend fun getCoordinates(query: String): GeoLocation? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null

        val key = query.trim().lowercase()

        // Cache hit: devolver resultado previo (incluye null = ciudad desconocida)
        if (sessionCache.containsKey(key)) {
            Log.d("Geocoding", "Cache hit para '$query'")
            return@withContext sessionCache[key]
        }

        try {
            // Usamos Nominatim (OpenStreetMap)
            // IMPORTANTE: User-Agent es obligatorio por política de uso de OSM
            val url = "https://nominatim.openstreetmap.org/search?q=${query.replace(" ", "+")}&format=json&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TrackerApp/1.0 (com.brk718.tracker)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: run {
                sessionCache[key] = null
                return@withContext null
            }

            val jsonArray = JSONArray(body)
            val result = if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                GeoLocation(obj.optDouble("lat"), obj.optDouble("lon"))
            } else null

            // Cachear resultado (incluido null: ciudad sin hit en Nominatim)
            sessionCache[key] = result
            result
        } catch (e: Exception) {
            Log.e("Geocoding", "Error geocoding '$query': ${e.message}")
            // NO cachear en excepción: puede ser error de red transitorio
            null
        }
    }
}
