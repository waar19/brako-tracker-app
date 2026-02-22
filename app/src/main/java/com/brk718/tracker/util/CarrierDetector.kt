package com.brk718.tracker.util

/**
 * Detecta el transportista a partir del formato del número de tracking.
 * Fuente única de verdad — usada por AddScreen (hint visual) y
 * AddShipmentViewModel (asignación real al guardar).
 */
object CarrierDetector {

    /**
     * Devuelve el nombre del transportista detectado, o null si el patrón
     * no coincide con ninguno conocido.
     */
    fun detect(tracking: String): String? {
        val t = tracking.trim()
        return when {
            // ── Amazon ──────────────────────────────────────────────────────
            // Número de pedido: 111-1234567-1234567
            t.matches(Regex("\\d{3}-\\d{7}-\\d{7}")) -> "Amazon"
            // TBA + al menos 12 caracteres (Amazon Logistics)
            t.startsWith("TBA", ignoreCase = true) && t.length >= 12 -> "Amazon"

            // ── UPS ──────────────────────────────────────────────────────────
            // 1Z + 16 caracteres alfanuméricos
            t.startsWith("1Z", ignoreCase = true) && t.length >= 18 -> "UPS"

            // ── FedEx (prefijo específico) ────────────────────────────────────
            t.startsWith("96") && t.length >= 20 -> "FedEx"

            // ── USPS (prefijos específicos o 20-22 dígitos) ──────────────────
            t.startsWith("94") && t.length >= 20 -> "USPS"
            t.startsWith("9400") || t.startsWith("9205") || t.startsWith("9361") -> "USPS"
            t.matches(Regex("\\d{20,22}")) -> "USPS"

            // ── DHL (prefijo JD específico) ───────────────────────────────────
            t.startsWith("JD", ignoreCase = true) && t.length >= 10 -> "DHL"

            // ── Carriers colombianos (deben ir ANTES de los catch-all de FedEx
            //    y DHL porque usan prefijos numéricos que solaparían) ──────────

            // Carriers con prefijo de letras (sin conflicto de orden)
            t.startsWith("DEP", ignoreCase = true) -> "Deprisa"
            t.startsWith("PIC", ignoreCase = true) || t.startsWith("PKP", ignoreCase = true) -> "Picap"
            t.matches(Regex("MU\\d{6,10}", RegexOption.IGNORE_CASE)) -> "Mensajeros Urbanos"
            t.matches(Regex("L\\d{8,12}", RegexOption.IGNORE_CASE)) -> "Listo"
            t.matches(Regex("T\\d{9}", RegexOption.IGNORE_CASE)) -> "Treda"
            t.matches(Regex("S\\d{10}", RegexOption.IGNORE_CASE)) -> "Speed"
            t.matches(Regex("C\\d{9,10}", RegexOption.IGNORE_CASE)) -> "Castores"

            // Carriers con prefijo numérico específico
            // (antes de FedEx \\d{12} y DHL \\d{10} para evitar colisión)
            t.matches(Regex("24\\d{10}")) -> "Interrapidísimo"   // 12 dígitos, empieza con 24
            t.matches(Regex("134\\d{8}")) -> "Avianca Cargo"     // 11 dígitos, empieza con 134
            t.matches(Regex("1\\d{12}")) -> "Envía"               // 13 dígitos, empieza con 1 (FedEx usa 12)
            t.matches(Regex("7\\d{9,11}")) -> "TCC"              // 10-12 dígitos, empieza con 7
            t.matches(Regex("3\\d{8,9}")) -> "Saferbo"           // 9-10 dígitos, empieza con 3
            t.matches(Regex("20\\d{6,7}")) -> "Deprisa"          // 8-9 dígitos, empieza con 20
            t.matches(Regex("[5-8]\\d{9}")) -> "Coordinadora"    // 10 dígitos, empieza con 5-8
            t.matches(Regex("9\\d{9,10}")) -> "Servientrega"     // 10-11 dígitos, empieza con 9

            // ── DHL (catch-all 10 dígitos genérico) ───────────────────────────
            t.matches(Regex("\\d{10}")) -> "DHL"

            // ── FedEx (catch-all 12 ó 15 dígitos genérico) ────────────────────
            t.matches(Regex("\\d{12}")) -> "FedEx"
            t.matches(Regex("\\d{15}")) -> "FedEx"

            else -> null
        }
    }
}
