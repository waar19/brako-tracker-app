package com.brk718.tracker.domain

data class ParsedShipment(
    val trackingNumber: String,
    val carrier: String,
    val title: String? = null
)

object EmailParser {

    /**
     * Busca tracking numbers en el texto de un email.
     * @param text Cuerpo del email (texto plano o HTML parseado)
     * @param sender Email del remitente (opcional, para determinar carrier)
     */
    fun findTrackingNumbers(text: String, sender: String? = null): List<ParsedShipment> {
        val results = mutableListOf<ParsedShipment>()
        val senderLower = sender?.lowercase() ?: ""

        // 1. Detectar carrier por sender (alta prioridad)
        val senderCarrier = detectCarrierFromSender(senderLower)

        // 2. Patrones específicos con prefijos (SIEMPRE buscar, alta confianza)
        addMatches(results, text, "Amazon / PASAREX", Regex("""\bAMZPSR\d{9,15}\b""", RegexOption.IGNORE_CASE))
        addMatches(results, text, "Amazon Logistics", Regex("""\bTBA\d{12,14}\b""", RegexOption.IGNORE_CASE))
        addMatches(results, text, "UPS", Regex("""\b1Z[0-9A-Z]{16}\b""", RegexOption.IGNORE_CASE))
        addMatches(results, text, "USPS", Regex("""\b9[2345]\d{20,24}\b"""))
        addMatches(results, text, "DHL", Regex("""\bJD\d{18}\b"""))

        // 3. Amazon tracking URLs (extraer el trackingId real del enlace "Rastrear")
        // Busca: trackingId, packageId, shipmentId, etc.
        val amazonTrackRegex = Regex("""(?:trackingId|tracking_number|packageId|shipmentId)[=:/]([A-Z0-9]{8,20})""", RegexOption.IGNORE_CASE)
        amazonTrackRegex.findAll(text).forEach { match ->
            val id = match.groupValues[1]
            if (results.none { it.trackingNumber == id }) {
                results.add(ParsedShipment(id, "Amazon", null))
            }
        }

        // 4. Si el sender es Amazon, no buscar patterns genéricos (evita falsos positivos)
        if (senderCarrier == "Amazon") {
            return results.distinctBy { it.trackingNumber }
        }

        // 5. Si conocemos el carrier por el sender → usar su patrón numérico
        if (senderCarrier != null) {
            val genericPattern = getGenericPattern(senderCarrier)
            if (genericPattern != null) {
                addMatches(results, text, senderCarrier, genericPattern)
            }
        }

        // 6. Si NO hay sender conocido, buscar patterns con contexto en el texto
        //    (palabras como "guía", "tracking", "envío" cerca del número)
        if (senderCarrier == null) {
            val contextualRegex = Regex(
                """(?:gu[ií]a|tracking|rastreo|seguimiento|n[úu]mero de env[ií]o)[:\s#]*(\d{8,20})""",
                RegexOption.IGNORE_CASE
            )
            contextualRegex.findAll(text).forEach { match ->
                val num = match.groupValues[1]
                if (num.length in 8..20 && results.none { it.trackingNumber == num }) {
                    results.add(ParsedShipment(num, "Desconocido", null))
                }
            }
        }

        return results.distinctBy { it.trackingNumber }
    }

    /**
     * Detecta el carrier basándose en el sender del email
     */
    private fun detectCarrierFromSender(sender: String): String? {
        return when {
            sender.contains("amazon") -> "Amazon"
            sender.contains("pasarex") -> "Amazon" // PASAREX = Amazon last-mile
            sender.contains("coordinadora") -> "Coordinadora"
            sender.contains("servientrega") -> "Servientrega"
            sender.contains("interrapidisimo") || sender.contains("inter rapidi") -> "Inter Rapidísimo"
            sender.contains("deprisa") -> "Deprisa"
            sender.contains("tcc") && !sender.contains("@") -> "TCC"
            sender.contains("@tcc") -> "TCC"
            sender.contains("envia") || sender.contains("colvanes") -> "Envía"
            sender.contains("clicoh") || sender.contains("logysto") -> "CliCOH"
            sender.contains("saferbo") -> "Saferbo"
            sender.contains("fedex") -> "FedEx"
            sender.contains("ups.com") || sender.contains("@ups") -> "UPS"
            sender.contains("dhl") -> "DHL"
            sender.contains("472") -> "472"
            sender.contains("mercadolibre") || sender.contains("mercadopago") -> "MercadoLibre"
            sender.contains("rappi") -> "Rappi"
            else -> null
        }
    }

    /**
     * Retorna el patrón regex genérico para un carrier conocido
     */
    private fun getGenericPattern(carrier: String): Regex? {
        return when (carrier) {
            "Servientrega" -> Regex("""\b\d{12}\b""")
            "Coordinadora" -> Regex("""\b\d{9,10}\b""")
            "Inter Rapidísimo" -> Regex("""\b\d{10,12}\b""")
            "Deprisa" -> Regex("""\b\d{10,14}\b""")
            "TCC" -> Regex("""\b\d{10,12}\b""")
            "Envía" -> Regex("""\b\d{10,12}\b""")
            "CliCOH" -> Regex("""\b\d{8,12}\b""")
            "Saferbo" -> Regex("""\b\d{9,12}\b""")
            "FedEx" -> Regex("""\b\d{12,22}\b""")
            "472" -> Regex("""\b472\d{7,10}\b""")
            "MercadoLibre" -> Regex("""\b\d{10,14}\b""")
            else -> null
        }
    }

    private fun addMatches(
        results: MutableList<ParsedShipment>,
        text: String,
        carrier: String,
        regex: Regex
    ) {
        regex.findAll(text).forEach { match ->
            val value = match.value
            if (value.length >= 8 && results.none { it.trackingNumber == value }) {
                results.add(ParsedShipment(value, carrier, null))
            }
        }
    }
}
