package com.brk718.tracker.data.remote

import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmazonScraper @Inject constructor() {

    data class ScrapedEvent(
        val date: String?,
        val message: String,
        val location: String?
    )

    data class ScrapedInfo(
        val status: String,
        val arrivalDate: String?,
        val location: String?, // For map (latest location)
        val progress: Int?, // 0-100
        val events: List<ScrapedEvent> = emptyList()
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

            // 3. Ubicación (para el mapa) e Historial completo
            var location: String? = null
            val events = mutableListOf<ScrapedEvent>()

            // Selectores para eventos (Mobile & Desktop fallbacks)
            // Mobile suele usar: .tracking-event-row o .transport-event-row
            val eventRows = doc.select("div.tracking-event-row, div.transport-event-row, div.a-row.tracking-event-row")
            
            if (eventRows.isNotEmpty()) {
                for (row in eventRows) {
                    val date = row.select("span.tracking-event-date-header, h2.transport-event-date-header").text()
                    val time = row.select("div.tracking-event-time, span.transport-event-time").text()
                    val message = row.select("div.tracking-event-message, div.transport-event-message").text()
                    val loc = row.select("span.tracking-event-location, div.transport-event-location").text()
                    
                    if (message.isNotBlank()) {
                         // Combinar fecha y hora
                        val fullDate = if (time.isNotBlank()) "$date $time".trim() else date
                        events.add(ScrapedEvent(fullDate, message, loc.takeIf { it.isNotBlank() }))
                    }
                }
            } else {
                 // Fallback para estructura antigua de escritorio (Table based?)
                 // Raramente necesario con Mobile UA, pero por si acaso
            }

            // Usar la ubicación del primer evento (más reciente) si existe
            if (events.isNotEmpty()) {
                location = events.first().location
            }
            
            // Si no encontramos eventos pero hay un "latest event" en el header
            if (events.isEmpty() && location == null) {
                 val headerLoc = doc.select("span.tracking-event-location").first() 
                    ?: doc.select("div.transport-event-location").first()
                 if (headerLoc != null) {
                     location = headerLoc.text()
                 }
            }

            return ScrapedInfo(
                status = status.takeIf { it.isNotEmpty() } ?: "No disponible",
                arrivalDate = arrivalDate.takeIf { it.isNotEmpty() } ?: null,
                location = location,
                progress = null,
                events = events
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
