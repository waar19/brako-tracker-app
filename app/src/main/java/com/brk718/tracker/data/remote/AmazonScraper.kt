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
            // URL de detalles del pedido
            val url = "https://www.amazon.com/gp/your-account/order-details?orderID=$orderId"
            
            val doc = Jsoup.connect(url)
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .timeout(10000)
                .get()

            // Lógica de scraping (aproximada, Amazon cambia esto a menudo)
            // Buscamos contenedores comunes de estado
            
            // Verificar si nos redirigió al login
            val title = doc.title()
            if (title.contains("Sign-In", ignoreCase = true) || 
                doc.select("form[name='signIn']").isNotEmpty()) {
                throw Exception("Sign-In required")
            }

            // Lógica de scraping (multi-selector para robustez)
            // 1. Estado principal
            var status = doc.select("div.shipment-top-status").text() // Mobile new
            if (status.isEmpty()) status = doc.select("h2.a-color-state").text() // Mobile generic
            if (status.isEmpty()) status = doc.select("div.js-shipment-info-container h2").text() // Desktop generic
            if (status.isEmpty()) status = doc.select("div.pt-delivery-card-primary-status").text() // Another mobile variant
            
            // Fallback: buscar cualquier texto grande en la cabecera
            if (status.isEmpty()) status = doc.select("h1.a-spacing-small").text()

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
