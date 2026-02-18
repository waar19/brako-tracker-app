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
            // URL de seguimiento "moderna" (la que usa el usuario)
            val url = "https://www.amazon.com/gp/your-account/ship-track?orderId=$orderId"
            
            val doc = Jsoup.connect(url)
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(10000)
                .get()

            // Verificar si nos redirigió al login
            val title = doc.title()
            if (title.contains("Sign-In", ignoreCase = true) || 
                doc.select("form[name='signIn']").isNotEmpty()) {
                throw Exception("Sign-In required")
            }

            // Lógica de scraping (multi-selector para robustez)
            // 1. Estado principal
            var status = doc.select("div.shipment-top-status").text()
            if (status.isEmpty()) status = doc.select("h3.a-spacing-small").text() // User provided selector
            if (status.isEmpty()) status = doc.select("h2.a-color-state").text()
            if (status.isEmpty()) status = doc.select("div.js-shipment-info-container h2").text()
            if (status.isEmpty()) status = doc.select("div.pt-delivery-card-primary-status").text()
            if (status.isEmpty()) status = doc.select("h1.a-spacing-small").text()
            
            // Generic Fallbacks for Desktop/Mobile
            if (status.isEmpty()) status = doc.select("div.shipment-status").text()
            if (status.isEmpty()) status = doc.select("span.shipment-status-label").text()
            if (status.isEmpty()) status = doc.select("h1").firstOrNull()?.text() ?: ""
            
            // Refinamiento: Si el estado es genérico
            if (status.equals("Detalles del pedido", ignoreCase = true) || 
                status.equals("Resumen del pedido", ignoreCase = true) ||
                status.contains("Order Details", ignoreCase = true)) {
                
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
            
            // DEBUG: Si no encontramos estado después de probar todo, devolvemos el título
            if (status.isEmpty()) {
                val title = doc.title()
                status = "Debug: $title"
            }
            // FIN DEBUG

            // 2. Fecha de entrega
            var arrivalDate = doc.select("span.arrival-date-text").text()
            if (arrivalDate.isEmpty()) arrivalDate = doc.select("span.promise-date").text()

            // 3. Ubicación (para el mapa)
            // Buscamos en el timeline el evento más reciente
            var location: String? = null
            val locationElement = doc.select("span.tracking-event-location").first() // Mobile list
            if (locationElement != null) {
                location = locationElement.text()
            } else {
                // Desktop list often has layout: Date | Time | Location | Description
                // We try to find a row with location text
                val rows = doc.select("div.a-row.tracking-event-row")
                if (rows.isNotEmpty()) {
                    val firstRow = rows.first()
                    // This is tricky without specific structure, usually column 3?
                    // Let's rely on mobile view mostly as we use mobile UA
                }
            }
            
            if (status.isEmpty()) {
                // Loguear para debug
                println("AmazonScraper: No status found. Title: $title")
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
