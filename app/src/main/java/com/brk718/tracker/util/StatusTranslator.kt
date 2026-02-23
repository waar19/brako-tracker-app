package com.brk718.tracker.util

/**
 * Traduce los tags y mensajes de estado de AfterShip (en inglés) al español.
 *
 * AfterShip usa tags estándar para clasificar el estado general del envío,
 * y subtag_message / checkpoint.message como texto descriptivo más detallado.
 * Los carriers colombianos ya devuelven texto en español; los internacionales
 * (FedEx, UPS, USPS, DHL, Amazon) devuelven texto en inglés.
 */
object StatusTranslator {

    // ─── Tags principales de AfterShip ────────────────────────────────────────
    // https://www.aftership.com/docs/tracking/quick-start/tracking-shipments/tracking-statuses

    fun translateTag(tag: String): String = when (tag) {
        "Pending"            -> "Pendiente"
        "InfoReceived"       -> "Información recibida"
        "InTransit"          -> "En tránsito"
        "OutForDelivery"     -> "En reparto"
        "AttemptFail"        -> "Intento fallido"
        "Delivered"          -> "Entregado"
        "AvailableForPickup" -> "Disponible para recoger"
        "Exception"          -> "Incidencia"
        "Expired"            -> "Expirado"
        // Amazon Logistics tags
        "PickedUp"           -> "Recolectado"
        "AtStation"          -> "En estación"
        else                 -> tag
    }

    // ─── Mensajes comunes de AfterShip subtag_message / checkpoint.message ────
    // IMPORTANTE: Las frases más largas deben ir primero para que el matching
    // por contenido no quede bloqueado por una frase más corta que también coincide.
    // Ej: "delivered to mailbox" debe estar antes que "delivered", si no siempre
    // gana "delivered" aunque el texto completo sea "delivered to mailbox".

    private val PHRASES: List<Pair<String, String>> = listOf(

        // ── Informativos / pre-envío ─────────────────────────────────────────
        "shipping information sent to fedex"          to "Información enviada a FedEx",
        "shipment information received"               to "Información del envío recibida",
        "shipping information received"               to "Información de envío recibida",
        "picked up - package available for clearance" to "Recolectado – disponible para despacho",
        "picked up by carrier"                        to "Recolectado por el transportista",
        "shipment picked up"                          to "Envío recolectado",
        "package accepted"                            to "Paquete aceptado",
        "package received"                            to "Paquete recibido",
        "order confirmed"                             to "Pedido confirmado",
        "order received"                              to "Pedido recibido",
        "order placed"                                to "Pedido realizado",
        "order shipped"                               to "Pedido enviado",
        "label created"                               to "Etiqueta creada",
        "label printed"                               to "Etiqueta impresa",
        "picked up"                                   to "Recolectado",

        // ── En tránsito ──────────────────────────────────────────────────────
        "in transit to next facility"                 to "En tránsito hacia la siguiente instalación",
        "arrived at destination facility"             to "Llegó a instalación de destino",
        "package transferred to destination facility" to "Paquete transferido a instalación de destino",
        "package transferred to post office"          to "Paquete transferido a correo",
        "arrived at transit facility"                 to "Llegó a instalación de tránsito",
        "departed transit facility"                   to "Salió de instalación de tránsito",
        "arrived at sort facility"                    to "Llegó a centro de clasificación",
        "departed sort facility"                      to "Salió del centro de clasificación",
        "processed at dhl facility"                   to "Procesado en instalación DHL",
        "arrived at carrier facility"                 to "Llegó a instalación del transportista",
        "arrived at facility"                         to "Llegó a instalación",
        "departed facility"                           to "Salió de instalación",
        "depart usps regional facility"               to "Salió de instalación regional USPS",
        "arrive usps regional facility"               to "Llegó a instalación regional USPS",
        "processed through facility"                  to "Procesado en instalación",
        "international shipment release"              to "Envío internacional liberado",
        "usps in possession of item"                  to "USPS tiene el artículo",
        "on fedex vehicle for delivery"               to "En vehículo FedEx para entrega",
        "your package is on its way"                  to "Tu paquete está en camino",
        "your package has been shipped"               to "Tu paquete ha sido enviado",
        "your shipment is on the way"                 to "Tu envío está en camino",
        "shipment in transit"                         to "Envío en tránsito",
        "package in transit"                          to "Paquete en tránsito",
        "at local post office"                        to "En oficina postal local",
        "with delivery courier"                       to "Con el repartidor",
        "customs clearance"                           to "Despacho aduanero",
        "cleared customs"                             to "Aduanas despachado",
        "customs cleared"                             to "Aduanas despachado",
        "import customs"                              to "Aduana de importación",
        "export customs"                              to "Aduana de exportación",
        "in customs"                                  to "En aduana",
        "sorting complete"                            to "Clasificación completada",
        "destination scan"                            to "Escaneo en destino",
        "origin scan"                                 to "Escaneo en origen",
        "departed hub"                                to "Salió del hub",
        "arrived at hub"                              to "Llegó al hub",
        "on the way"                                  to "En camino",
        "in transit"                                  to "En tránsito",
        "transit"                                     to "En tránsito",
        "acceptance"                                  to "Aceptado",

        // ── En reparto ───────────────────────────────────────────────────────
        "on vehicle for delivery"                     to "En vehículo de reparto",
        "with delivery agent"                         to "Con el agente de entrega",
        "delivery in progress"                        to "Entrega en progreso",
        "delivery attempted"                          to "Intento de entrega",
        "out for delivery"                            to "En reparto",

        // ── Entregado ────────────────────────────────────────────────────────
        "your package has been delivered"             to "Tu paquete ha sido entregado",
        "delivered - left at door"                    to "Entregado – dejado en la puerta",
        "delivered to parcel locker"                  to "Entregado en casillero",
        "delivered to reception"                      to "Entregado en recepción",
        "delivered to neighbor"                       to "Entregado a vecino",
        "delivered to mailbox"                        to "Entregado en buzón",
        "delivered - signed"                          to "Entregado – firmado",
        "shipment delivered"                          to "Envío entregado",
        "package delivered"                           to "Paquete entregado",
        "signed for by"                               to "Firmado por",
        "delivered"                                   to "Entregado",

        // ── Disponible para recoger ──────────────────────────────────────────
        "available for pickup"                        to "Disponible para recoger",
        "delivery notice left"                        to "Aviso de entrega dejado",
        "held at customs"                             to "Retenido en aduana",
        "held at post office"                         to "Retenido en oficina postal",
        "notice left"                                 to "Aviso dejado",

        // ── Intentos fallidos ────────────────────────────────────────────────
        "delivery attempt failed"                     to "Intento de entrega fallido",
        "recipient not available"                     to "Destinatario no disponible",
        "insufficient address"                        to "Dirección insuficiente",
        "incorrect address"                           to "Dirección incorrecta",
        "address issue"                               to "Problema con la dirección",
        "wrong address"                               to "Dirección incorrecta",
        "delivery failed"                             to "Entrega fallida",
        "unable to deliver"                           to "No se pudo entregar",
        "no one home"                                 to "Nadie en casa",

        // ── Excepciones / incidencias ────────────────────────────────────────
        "returning to sender"                         to "Devolviendo al remitente",
        "returned to sender"                          to "Devuelto al remitente",
        "return to sender"                            to "Devuelto al remitente",
        "refused by recipient"                        to "Rechazado por el destinatario",
        "package damaged"                             to "Paquete dañado",
        "package lost"                                to "Paquete perdido",
        "shipment delay"                              to "Retraso en el envío",
        "weather delay"                               to "Retraso por clima",
        "exception"                                   to "Incidencia",
        "delayed"                                     to "Retrasado",
        "damaged"                                     to "Dañado",
        "delay"                                       to "Retraso",
        "lost"                                        to "Perdido",

        // ── Expirado ─────────────────────────────────────────────────────────
        "shipment expired"                            to "Envío expirado",
        "expired"                                     to "Expirado"
    )

    // Mapa para búsqueda exacta O(1)
    private val PHRASE_MAP: Map<String, String> = PHRASES.toMap()

    /**
     * Intenta traducir un mensaje de estado al español.
     * Estrategia:
     * 1. Si parece español, devolver tal cual.
     * 2. Búsqueda exacta en el mapa.
     * 3. Búsqueda por prefijo (ej. "Delivered to John Smith" → "Entregado — John Smith").
     * 4. Búsqueda por contenido en orden de longitud (frases más largas primero).
     * 5. Devolver original si no hay traducción.
     */
    fun translateMessage(message: String): String {
        if (message.isBlank()) return message
        if (looksLikeSpanish(message)) return message

        val lower = message.lowercase().trim()

        // 1. Coincidencia exacta
        PHRASE_MAP[lower]?.let { return it }

        // 2. Coincidencia por prefijo (ej. "Delivered to John Smith")
        for ((key, translation) in PHRASES) {
            if (lower.startsWith(key)) {
                val suffix = message.substring(key.length).trim()
                return if (suffix.isBlank()) translation else "$translation — $suffix"
            }
        }

        // 3. Coincidencia por contenido (PHRASES ya está ordenada de mayor a menor longitud
        //    dentro de cada categoría, así las frases específicas ganan a las genéricas)
        for ((key, translation) in PHRASES) {
            if (lower.contains(key)) return translation
        }

        return message
    }

    // ─── Heurística para detectar si el texto ya está en español ──────────────
    // Se usan palabras/frases de ≥4 caracteres para evitar falsos positivos con
    // partículas como "de", "en" que también existen en inglés.

    private val SPANISH_MARKERS = setOf(
        "entregado", "tránsito", "transito", "reparto", "pendiente", "incidencia",
        "recibido", "enviado", "envío", "paquete", "novedad", "clasificación",
        "instalación", "recolectado", "disponible", "retenido", "expirado",
        "llegó", "salió", "está", "para recoger", "intento", "retrasado",
        "devuelto", "remitente", "dañado", "perdido", "firmado"
    )

    private fun looksLikeSpanish(text: String): Boolean {
        val lower = text.lowercase()
        return SPANISH_MARKERS.any { lower.contains(it) }
    }
}
