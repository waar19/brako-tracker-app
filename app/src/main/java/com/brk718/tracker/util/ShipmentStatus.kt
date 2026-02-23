package com.brk718.tracker.util

/**
 * Substrings canónicos de estado de envío en español (en minúsculas).
 *
 * Centraliza las cadenas de estado para evitar literales repetidos en múltiples
 * archivos. Usar siempre con `status.lowercase().contains(ShipmentStatus.X)`.
 */
object ShipmentStatus {
    const val DELIVERED        = "entregado"
    const val IN_TRANSIT       = "en tránsito"
    const val IN_TRANSIT_ALT   = "en transito"   // sin tilde (fallback)
    const val ON_THE_WAY       = "en camino"
    const val OUT_FOR_DELIVERY = "en reparto"
    const val EXCEPTION        = "incidencia"
    const val PROBLEM          = "problema"
    const val FAILED_ATTEMPT   = "intento fallido"
}
