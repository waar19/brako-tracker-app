package com.brk718.tracker.data.remote

import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmazonScraper @Inject constructor() {

    data class ScrapedInfo(
        val status: String,
        val arrivalDate: String?,
        val location: String?, // For map
        val progress: Int? // 0-100
    )

    fun scrapeOrder(orderId: String, cookies: String): ScrapedInfo {
        try {
            // ESTRATEGIA DE DOS PASOS:
            // 1. Ir a detalles del pedido para encontrar el enlace REAL de rastreo (con shipmentId)
            val url = "https://www.amazon.com/gp/your-account/order-details?orderID=$orderId"
            
            // Usamos Mobile UA para coincidir con el WebView de Auth y obtener DOM más simple
            val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            var doc = Jsoup.connect(url)
                .header("Cookie", cookies)
                .header("User-Agent", userAgent)
                .timeout(10000)
                .get()

            // Verificar si nos redirigió al login
            var title = doc.title()
            if (title.contains("Sign-In", ignoreCase = true) || 
                doc.select("form[name='signIn']").isNotEmpty()) {
                throw Exception("Sign-In required")
            }

            // 2. Buscar el enlace de "Rastrear paquete"
            val trackingLink = doc.select("a:contains(Track package)").firstOrNull()?.attr("abs:href") 
                ?: doc.select("a[href*='ship-track']").firstOrNull()?.attr("abs:href")
            
            if (!trackingLink.isNullOrBlank()) {
                // Si encontramos el enlace, lo seguimos
                try {
                    doc = Jsoup.connect(trackingLink)
                        .header("Cookie", cookies)
                        .header("User-Agent", userAgent)
                        .timeout(10000)
                        .get()
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Si falla, nos quedamos con la página de detalles
                }
            }

            // Lógica de scraping (Mobile First)
            // 1. Estado principal
            var status = doc.select("div.shipment-top-status").text() // A veces presente en mobile view web
            if (status.isEmpty()) status = doc.select("h3.a-spacing-small").text()
            if (status.isEmpty()) status = doc.select("h2.a-color-state").text() // Típico header de estado
            if (status.isEmpty()) status = doc.select("div.js-shipment-info-container h2").text()
            if (status.isEmpty()) status = doc.select("div.pt-delivery-card-primary-status").text()
            
            // Mobile Specific
            if (status.isEmpty()) status = doc.select("div.transport-message").text()
            if (status.isEmpty()) status = doc.select("h5.transport-status-header").text()
            
            // Refinamiento: Si el estado es genérico o vacío
            if (status.isEmpty() || 
                status.equals("Detalles del pedido", ignoreCase = true) || 
                status.equals("Resumen del pedido", ignoreCase = true) ||
                status.contains("Order Details", ignoreCase = true)) {
                
                // Intentar buscar mensajes de evento específicos
                val eventMessage = doc.select("div.tracking-event-message").firstOrNull()?.text()
                if (!eventMessage.isNullOrBlank()) {
                    status = eventMessage
                } else {
                    val mobileEvent = doc.select("div.transport-event-message").firstOrNull()?.text()
                    if (!mobileEvent.isNullOrBlank()) {
                        status = mobileEvent
                    } else {
                        // Último recurso: Buscar palabras clave en todo el texto del cuerpo
                        val bodyText = doc.body().text()
                        when {
                            bodyText.contains("Entregado", ignoreCase = true) -> status = "Entregado"
                            bodyText.contains("Llega mañana", ignoreCase = true) -> status = "Llega mañana"
                            bodyText.contains("Llega hoy", ignoreCase = true) -> status = "Llega hoy"
                            bodyText.contains("En camino", ignoreCase = true) -> status = "En camino"
                            bodyText.contains("En tránsito", ignoreCase = true) -> status = "En tránsito"
                            bodyText.contains("Tu paquete", ignoreCase = true) -> status = "En reparto"
                        }
                    }
                }
            }
            
            // 2. Fecha de entrega
            var arrivalDate = doc.select("span.arrival-date-text").text()
            if (arrivalDate.isEmpty()) arrivalDate = doc.select("span.promise-date").text()
            if (arrivalDate.isEmpty()) arrivalDate = doc.select("div.promise-message").text()

            // 3. Ubicación (para el mapa)
            // Buscamos en el timeline el evento más reciente
            var location: String? = null
            
            // Mobile list usually has .tracking-event-location or .transport-event-location
            val locationElement = doc.select("span.tracking-event-location").first() 
                ?: doc.select("div.transport-event-location").first()
                
            if (locationElement != null) {
                location = locationElement.text()
            }
            
            return ScrapedInfo(
                status = status.takeIf { it.isNotEmpty() } ?: "No disponible",
                arrivalDate = arrivalDate.takeIf { it.isNotEmpty() } ?: null,
                location = location,
                progress = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
