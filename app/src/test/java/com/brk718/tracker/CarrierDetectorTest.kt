package com.brk718.tracker

import com.brk718.tracker.util.CarrierDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests para CarrierDetector.
 * Verifica que cada patrón de número de tracking detecta el carrier correcto.
 */
class CarrierDetectorTest {

    // ── Amazon ──────────────────────────────────────────────────────────────────
    @Test fun amazon_order_number() =
        assertEquals("Amazon", CarrierDetector.detect("111-1234567-1234567"))

    @Test fun amazon_tba() =
        assertEquals("Amazon", CarrierDetector.detect("TBA123456789012"))

    @Test fun amazon_tba_lowercase() =
        assertEquals("Amazon", CarrierDetector.detect("tba123456789012"))

    // ── UPS ──────────────────────────────────────────────────────────────────────
    @Test fun ups() =
        assertEquals("UPS", CarrierDetector.detect("1Z999AA10123456784"))

    @Test fun ups_lowercase() =
        assertEquals("UPS", CarrierDetector.detect("1z999AA10123456784"))

    // ── FedEx ────────────────────────────────────────────────────────────────────
    @Test fun fedex_12_digits() =
        assertEquals("FedEx", CarrierDetector.detect("123456789012"))

    @Test fun fedex_15_digits() =
        assertEquals("FedEx", CarrierDetector.detect("123456789012345"))

    // ── USPS ─────────────────────────────────────────────────────────────────────
    @Test fun usps_20_digits() =
        assertEquals("USPS", CarrierDetector.detect("12345678901234567890"))

    @Test fun usps_9400_prefix() =
        assertEquals("USPS", CarrierDetector.detect("9400111899223397018578"))

    // ── DHL ──────────────────────────────────────────────────────────────────────
    @Test fun dhl_JD_prefix() =
        assertEquals("DHL", CarrierDetector.detect("JD014600006228097890"))

    @Test fun dhl_10_digits() =
        assertEquals("DHL", CarrierDetector.detect("1234567890"))

    // ── Carriers colombianos ─────────────────────────────────────────────────────
    @Test fun interrapidisimo() =
        assertEquals("Interrapidísimo", CarrierDetector.detect("241234567890"))

    @Test fun coordinadora() =
        assertEquals("Coordinadora", CarrierDetector.detect("5123456789"))

    @Test fun servientrega() =
        assertEquals("Servientrega", CarrierDetector.detect("9123456789"))

    @Test fun envia() =
        assertEquals("Envía", CarrierDetector.detect("1123456789012"))

    @Test fun listo() =
        assertEquals("Listo", CarrierDetector.detect("L123456789"))

    @Test fun treda() =
        assertEquals("Treda", CarrierDetector.detect("T123456789"))

    @Test fun speed() =
        assertEquals("Speed", CarrierDetector.detect("S1234567890"))

    @Test fun castores() =
        assertEquals("Castores", CarrierDetector.detect("C123456789"))

    @Test fun avianca_cargo() =
        assertEquals("Avianca Cargo", CarrierDetector.detect("13412345678"))

    @Test fun deprisa_dep_prefix() =
        assertEquals("Deprisa", CarrierDetector.detect("DEP1234567"))

    @Test fun deprisa_20_prefix() =
        assertEquals("Deprisa", CarrierDetector.detect("201234567"))

    @Test fun picap() =
        assertEquals("Picap", CarrierDetector.detect("PIC123456"))

    @Test fun mensajeros_urbanos() =
        assertEquals("Mensajeros Urbanos", CarrierDetector.detect("MU1234567890"))

    // ── Desconocido ──────────────────────────────────────────────────────────────
    @Test fun unknown_returns_null() =
        assertNull(CarrierDetector.detect("XYZXYZXYZ"))

    @Test fun empty_returns_null() =
        assertNull(CarrierDetector.detect(""))

    @Test fun whitespace_trimmed_and_detected() =
        assertEquals("Amazon", CarrierDetector.detect("  111-1234567-1234567  "))
}
