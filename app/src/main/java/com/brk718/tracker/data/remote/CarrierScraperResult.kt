package com.brk718.tracker.data.remote

/**
 * Resultado devuelto por cualquier scraper de carrier colombiano.
 * Mismo contrato que InterrapidisimoScraper.TrackingResult pero compartido entre todos.
 */
data class CarrierScraperResult(
    val status: String?,
    val events: List<CarrierScraperEvent>,
    val error: String? = null
)

data class CarrierScraperEvent(
    val timestamp: String,
    val description: String,
    val location: String
)

/**
 * Interfaz común para todos los scrapers de carriers colombianos.
 * Cada implementación es @Singleton y se inyecta vía [CarrierScraperFactory].
 */
interface ColombianCarrierScraper {
    suspend fun getTracking(trackingNumber: String): CarrierScraperResult
}
