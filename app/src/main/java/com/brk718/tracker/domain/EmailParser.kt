package com.brk718.tracker.domain

data class ParsedShipment(
    val trackingNumber: String,
    val carrier: String
)

object EmailParser {

    private val CARRIERS = mapOf(
        "UPS" to Regex("""\b1Z[0-9A-Z]{16}\b"""),
        "FedEx" to Regex("""\b[0-9]{12}\b"""), // Simplificado
        "USPS" to Regex("""\b(9[2345]\d{20,24})\b"""),
        "DHL" to Regex("""\b[0-9]{10}\b"""),
        "Amazon Logistics" to Regex("""\bTBA[0-9]{12}\b""")
    )

    fun findTrackingNumbers(text: String): List<ParsedShipment> {
        val results = mutableListOf<ParsedShipment>()

        CARRIERS.forEach { (carrier, regex) ->
            regex.findAll(text).forEach { match ->
                results.add(ParsedShipment(match.value, carrier))
            }
        }
        
        // Amazon específico: buscar enlaces de "track-package" si no hay TBA
        if (text.contains("amazon.com") || text.contains("amazon.es")) {
            // Lógica adicional para Amazon si es necesario
        }

        return results.distinctBy { it.trackingNumber }
    }
}
