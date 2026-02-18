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
            
            // 1. Estado principal
            var status = doc.select("div.shipment-top-status").text()
            if (status.isEmpty()) status = doc.select("h2.a-color-state").text()
            if (status.isEmpty()) status = "No disponible"

            // 2. Fecha de entrega
            val arrivalDate = doc.select("span.arrival-date-text").text().takeIf { it.isNotEmpty() }
            
            // 3. Ubicación (para el mapa) - Buscamos en el timeline o texto de tracking
            // A menudo está en div.tracking-event-location o similar
            val location = doc.select("span.tracking-event-location").last()?.text()

            // 4. Progreso (barra)
            // A veces es un width: X%
            // O una clase step-active
            
            return ScrapedInfo(
                status = status,
                arrivalDate = arrivalDate,
                location = location,
                progress = null // Implementar lógica de barra si es posible
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
