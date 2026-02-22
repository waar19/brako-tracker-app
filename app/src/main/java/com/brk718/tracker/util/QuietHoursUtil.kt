package com.brk718.tracker.util

import java.util.Calendar

/**
 * Lógica de "horas de silencio" para notificaciones.
 * Extraída de SyncWorker para permitir unit tests sin dependencias de Android.
 */
object QuietHoursUtil {

    /**
     * Devuelve true si la hora actual está dentro del rango de silencio.
     * Maneja rangos que cruzan medianoche (ej. 23:00 → 07:00).
     *
     * @param enabled            Si las quiet hours están activadas
     * @param startHour          Hora de inicio (0-23)
     * @param startMinute        Minuto de inicio (0-59)
     * @param endHour            Hora de fin (0-23)
     * @param endMinute          Minuto de fin (0-59)
     * @param currentMinuteOfDay Minuto del día actual (HOUR_OF_DAY * 60 + MINUTE).
     *                           Por defecto lee el reloj del sistema; se puede inyectar
     *                           en tests para simular cualquier hora sin mocks.
     */
    fun isInQuietHours(
        enabled: Boolean,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        currentMinuteOfDay: Int = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
    ): Boolean {
        if (!enabled) return false
        val startMin = startHour * 60 + startMinute
        val endMin   = endHour   * 60 + endMinute
        return if (startMin <= endMin) {
            // Rango dentro del mismo día (ej. 02:30 → 08:00)
            currentMinuteOfDay in startMin until endMin
        } else {
            // Rango cruzando medianoche (ej. 23:30 → 07:00)
            currentMinuteOfDay >= startMin || currentMinuteOfDay < endMin
        }
    }
}
