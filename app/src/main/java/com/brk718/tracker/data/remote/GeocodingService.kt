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

    suspend fun getCoordinates(query: String): GeoLocation? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null

        try {
            // Usamos Nominatim (OpenStreetMap)
            // IMPORTANTE: User-Agent es obligatorio por política de uso de OSM
            val url = "https://nominatim.openstreetmap.org/search?q=${query.replace(" ", "+")}&format=json&limit=1"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TrackerApp/1.0 (com.brk718.tracker)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            
            val jsonArray = JSONArray(body)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                val lat = obj.optDouble("lat")
                val lon = obj.optDouble("lon")
                return@withContext GeoLocation(lat, lon)
            }
        } catch (e: Exception) {
            Log.e("Geocoding", "Error geocoding '$query': ${e.message}")
        }
        return@withContext null
    }
}
