package com.brk718.tracker

import com.brk718.tracker.util.QuietHoursUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests para QuietHoursUtil.
 *
 * Se inyecta currentMinuteOfDay directamente en cada llamada para simular
 * cualquier hora del día sin mocks ni dependencias de Android.
 *
 * Notación de horas usada en los comentarios: H:MM → minutosDelDía
 *   00:00 →    0
 *   02:00 →  120
 *   06:00 →  360
 *   07:00 →  420
 *   08:00 →  480
 *   12:00 →  720
 *   22:00 → 1320
 *   23:00 → 1380
 *   23:30 → 1410
 */
class QuietHoursUtilTest {

    // ── Disabled ──────────────────────────────────────────────────────────────

    @Test
    fun `disabled returns false regardless of time`() {
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = false,
                startHour = 22, startMinute = 0,
                endHour = 8, endMinute = 0,
                currentMinuteOfDay = 120   // 02:00
            )
        )
    }

    @Test
    fun `disabled returns false even inside the range`() {
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = false,
                startHour = 0, startMinute = 0,
                endHour = 23, endMinute = 59,
                currentMinuteOfDay = 720   // 12:00
            )
        )
    }

    // ── Rango que NO cruza medianoche (ej. 02:30 → 08:00) ────────────────────

    @Test
    fun `same-day range - current time inside returns true`() {
        assertTrue(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 2, startMinute = 30,
                endHour = 8, endMinute = 0,
                currentMinuteOfDay = 300   // 05:00 — dentro del rango
            )
        )
    }

    @Test
    fun `same-day range - current time before range returns false`() {
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 2, startMinute = 30,
                endHour = 8, endMinute = 0,
                currentMinuteOfDay = 60    // 01:00 — antes del inicio
            )
        )
    }

    @Test
    fun `same-day range - current time after range returns false`() {
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 2, startMinute = 30,
                endHour = 8, endMinute = 0,
                currentMinuteOfDay = 720   // 12:00 — después del fin
            )
        )
    }

    @Test
    fun `same-day range - at exact start boundary returns true`() {
        assertTrue(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 2, startMinute = 30,
                endHour = 8, endMinute = 0,
                currentMinuteOfDay = 150   // 02:30 — exactamente en el inicio (inclusive)
            )
        )
    }

    @Test
    fun `same-day range - at exact end boundary returns false`() {
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 2, startMinute = 30,
                endHour = 8, endMinute = 0,
                currentMinuteOfDay = 480   // 08:00 — exactamente en el fin (exclusive)
            )
        )
    }

    // ── Rango que cruza medianoche (ej. 23:00 → 07:00) ───────────────────────

    @Test
    fun `midnight-crossing range - current time after start returns true`() {
        assertTrue(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 23, startMinute = 0,
                endHour = 7, endMinute = 0,
                currentMinuteOfDay = 1410  // 23:30 — después de las 23:00
            )
        )
    }

    @Test
    fun `midnight-crossing range - current time before end returns true`() {
        assertTrue(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 23, startMinute = 0,
                endHour = 7, endMinute = 0,
                currentMinuteOfDay = 120   // 02:00 — antes de las 07:00
            )
        )
    }

    @Test
    fun `midnight-crossing range - current time in midday gap returns false`() {
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 23, startMinute = 0,
                endHour = 7, endMinute = 0,
                currentMinuteOfDay = 720   // 12:00 — fuera del rango
            )
        )
    }

    @Test
    fun `midnight-crossing range - current time just before start returns false`() {
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 23, startMinute = 30,
                endHour = 6, endMinute = 0,
                currentMinuteOfDay = 1380  // 23:00 — antes del inicio 23:30
            )
        )
    }

    @Test
    fun `midnight-crossing range - at midnight returns true`() {
        assertTrue(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 23, startMinute = 0,
                endHour = 7, endMinute = 0,
                currentMinuteOfDay = 0     // 00:00 — medianoche exacta
            )
        )
    }

    @Test
    fun `midnight-crossing range - at exact start boundary returns true`() {
        assertTrue(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 23, startMinute = 0,
                endHour = 7, endMinute = 0,
                currentMinuteOfDay = 1380  // 23:00 — exactamente en el inicio (inclusive)
            )
        )
    }

    @Test
    fun `midnight-crossing range - at exact end boundary returns false`() {
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 23, startMinute = 0,
                endHour = 7, endMinute = 0,
                currentMinuteOfDay = 420   // 07:00 — exactamente en el fin (exclusive)
            )
        )
    }

    // ── Rangos con minutos ────────────────────────────────────────────────────

    @Test
    fun `range with minutes - inside returns true`() {
        assertTrue(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 22, startMinute = 30,
                endHour = 7, endMinute = 45,
                currentMinuteOfDay = 1350  // 22:30 — en el inicio
            )
        )
    }

    @Test
    fun `range with minutes - one minute before end boundary returns true`() {
        assertTrue(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 22, startMinute = 30,
                endHour = 7, endMinute = 45,
                currentMinuteOfDay = 464   // 07:44 — un minuto antes del fin
            )
        )
    }

    @Test
    fun `range with minutes - at end boundary returns false`() {
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 22, startMinute = 30,
                endHour = 7, endMinute = 45,
                currentMinuteOfDay = 465   // 07:45 — exactamente en el fin (exclusive)
            )
        )
    }

    // ── Casos extremos ────────────────────────────────────────────────────────

    @Test
    fun `start equals end - no time is inside`() {
        // startMin == endMin → rango mismo día → in startMin until startMin → siempre vacío
        assertFalse(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 8, startMinute = 0,
                endHour = 8, endMinute = 0,
                currentMinuteOfDay = 480   // 08:00 — igual al inicio/fin
            )
        )
    }

    @Test
    fun `last minute of day 23 59 is inside midnight-crossing range`() {
        assertTrue(
            QuietHoursUtil.isInQuietHours(
                enabled = true,
                startHour = 23, startMinute = 0,
                endHour = 6, endMinute = 0,
                currentMinuteOfDay = 1439  // 23:59 — último minuto del día
            )
        )
    }
}
