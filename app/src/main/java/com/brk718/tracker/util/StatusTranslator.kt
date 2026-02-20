package com.brk718.tracker.util

/**
 * Traduce los tags y mensajes de estado de AfterShip (en inglés) al español.
 *
 * AfterShip usa tags estándar para clasificar el estado general del envío,
 * y subtag_message / checkpoint.message como texto descriptivo más detallado.
 * Los carriers colombianos ya devuelven texto en español; los internacionales
 * (FedEx, UPS, USPS, DHL) y la propia API devuelven texto en inglés.
 */
object StatusTranslator {

    // ─── Tags principales de AfterShip ────────────────────────────────────────
    // https://www.aftership.com/docs/tracking/quick-start/tracking-shipments/tracking-statuses

    fun translateTag(tag: String): String = when (tag) {
        "Pending"         -> "Pendiente"
        "InfoReceived"    -> "Información recibida"
        "InTransit"       -> "En tránsito"
        "OutForDelivery"  -> "En reparto"
        "AttemptFail"     -> "Intento fallido"
        "Delivered"       -> "Entregado"
        "AvailableForPickup" -> "Disponible para recoger"
        "Exception"       -> "Incidencia"
        "Expired"         -> "Expirado"
        else              -> tag
    }

    // ─── Mensajes comunes de AfterShip subtag_message / checkpoint.message ────
    // Mapa de frases en inglés → español. La clave debe estar en minúsculas.

    private val PHRASE_MAP: Map<String, String> = mapOf(
        // Informativos / pre-envío
        "shipping information received"        to "Información de envío recibida",
        "shipment information received"        to "Información del envío recibida",
        "label created"                        to "Etiqueta creada",
        "order placed"                         to "Pedido realizado",
        "order received"                       to "Pedido recibido",
        "order confirmed"                      to "Pedido confirmado",
        "package accepted"                     to "Paquete aceptado",
        "package received"                     to "Paquete recibido",
        "picked up"                            to "Recolectado",
        "picked up by carrier"                 to "Recolectado por el transportista",
        "shipment picked up"                   to "Envío recolectado",

        // En tránsito
        "in transit"                           to "En tránsito",
        "in transit to next facility"          to "En tránsito hacia la siguiente instalación",
        "departed facility"                    to "Salió de instalación",
        "arrived at facility"                  to "Llegó a instalación",
        "arrived at sort facility"             to "Llegó a centro de clasificación",
        "departed sort facility"               to "Salió del centro de clasificación",
        "arrived at transit facility"          to "Llegó a instalación de tránsito",
        "departed transit facility"            to "Salió de instalación de tránsito",
        "arrived at destination facility"      to "Llegó a instalación de destino",
        "arrived at hub"                       to "Llegó al hub",
        "departed hub"                         to "Salió del hub",
        "in customs"                           to "En aduana",
        "customs clearance"                    to "Despacho aduanero",
        "cleared customs"                      to "Aduanas despachado",
        "customs cleared"                      to "Aduanas despachado",
        "import customs"                       to "Aduana de importación",
        "export customs"                       to "Aduana de exportación",
        "on the way"                           to "En camino",
        "package in transit"                   to "Paquete en tránsito",
        "shipment in transit"                  to "Envío en tránsito",
        "at local post office"                 to "En oficina postal local",
        "with delivery courier"                to "Con el repartidor",

        // En reparto
        "out for delivery"                     to "En reparto",
        "on vehicle for delivery"              to "En vehículo de reparto",
        "with delivery agent"                  to "Con el agente de entrega",
        "delivery in progress"                 to "Entrega en progreso",
        "delivery attempted"                   to "Intento de entrega",

        // Entregado
        "delivered"                            to "Entregado",
        "delivered to mailbox"                 to "Entregado en buzón",
        "delivered to neighbor"                to "Entregado a vecino",
        "delivered to reception"               to "Entregado en recepción",
        "delivered to parcel locker"           to "Entregado en casillero",
        "delivered - left at door"             to "Entregado - dejado en la puerta",
        "package delivered"                    to "Paquete entregado",
        "shipment delivered"                   to "Envío entregado",
        "signed for by"                        to "Firmado por",

        // Disponible para recoger
        "available for pickup"                 to "Disponible para recoger",
        "held at customs"                      to "Retenido en aduana",
        "held at post office"                  to "Retenido en oficina postal",
        "notice left"                          to "Aviso dejado",
        "delivery notice left"                 to "Aviso de entrega dejado",

        // Intentos fallidos
        "delivery attempt failed"              to "Intento de entrega fallido",
        "delivery failed"                      to "Entrega fallida",
        "unable to deliver"                    to "No se pudo entregar",
        "recipient not available"              to "Destinatario no disponible",
        "no one home"                          to "Nadie en casa",
        "wrong address"                        to "Dirección incorrecta",
        "address issue"                        to "Problema con la dirección",
        "insufficient address"                 to "Dirección insuficiente",
        "incorrect address"                    to "Dirección incorrecta",

        // Excepciones / incidencias
        "exception"                            to "Incidencia",
        "delay"                                to "Retraso",
        "delayed"                              to "Retrasado",
        "shipment delay"                       to "Retraso en el envío",
        "weather delay"                        to "Retraso por clima",
        "damaged"                              to "Dañado",
        "package damaged"                      to "Paquete dañado",
        "lost"                                 to "Perdido",
        "package lost"                         to "Paquete perdido",
        "return to sender"                     to "Devuelto al remitente",
        "returning to sender"                  to "Devolviendo al remitente",
        "returned to sender"                   to "Devuelto al remitente",
        "refused by recipient"                 to "Rechazado por el destinatario",

        // Expirado
        "expired"                              to "Expirado",
        "shipment expired"                     to "Envío expirado",

        // FedEx específico
        "shipment information sent to fedex"   to "Información enviada a FedEx",
        "picked up - package available for clearance" to "Recolectado – disponible para despacho",
        "on fedex vehicle for delivery"        to "En vehículo FedEx para entrega",
        "international shipment release"       to "Envío internacional liberado",

        // UPS específico
        "your package is on its way"           to "Tu paquete está en camino",
        "package transferred to post office"   to "Paquete transferido a correo",
        "destination scan"                     to "Escaneo en destino",
        "origin scan"                          to "Escaneo en origen",

        // USPS específico
        "usps in possession of item"           to "USPS tiene el artículo",
        "acceptance"                           to "Aceptado",
        "depart usps regional facility"        to "Salió de instalación regional USPS",
        "arrive usps regional facility"        to "Llegó a instalación regional USPS",
        "processed through facility"           to "Procesado en instalación",
        "sorting complete"                     to "Clasificación completada",

        // DHL específico
        "shipment picked up"                   to "Envío recolectado",
        "processed at dhl facility"            to "Procesado en instalación DHL",
        "transit"                              to "En tránsito",
        "delivered - signed"                   to "Entregado – firmado"
    )

    /**
     * Intenta traducir un mensaje de estado al español.
     * Si no hay traducción exacta, busca si alguna frase conocida está contenida en el mensaje.
     * Si no encuentra nada, devuelve el texto original.
     */
    fun translateMessage(message: String): String {
        if (message.isBlank()) return message

        // Si ya parece estar en español (contiene palabras comunes en español), devolver tal cual
        if (looksLikeSpanish(message)) return message

        val lower = message.lowercase().trim()

        // Coincidencia exacta
        PHRASE_MAP[lower]?.let { return it }

        // Coincidencia por prefijo (ej. "Delivered to John Smith" → "Entregado")
        for ((key, translation) in PHRASE_MAP) {
            if (lower.startsWith(key)) {
                val suffix = message.substring(key.length).trim()
                return if (suffix.isBlank()) translation else "$translation — $suffix"
            }
        }

        // Coincidencia por contenido (ej. "Package in transit to Miami" → detecta "in transit")
        for ((key, translation) in PHRASE_MAP) {
            if (lower.contains(key)) return translation
        }

        // Sin traducción conocida: devolver original
        return message
    }

    // ─── Heurística simple para detectar si el texto ya está en español ────────

    private val SPANISH_MARKERS = setOf(
        "en ", "de ", "del", "con ", "por ", "para ", "está", "llegó", "salió",
        "entregado", "tránsito", "reparto", "pendiente", "incidencia", "recibido",
        "envío", "paquete", "novedad", "clasificación", "instalación", "recolectado"
    )

    private fun looksLikeSpanish(text: String): Boolean {
        val lower = text.lowercase()
        return SPANISH_MARKERS.any { lower.contains(it) }
    }
}
