package com.brk718.tracker.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brk718.tracker.MainActivity
import com.brk718.tracker.R
import com.brk718.tracker.TrackerApp
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
import com.brk718.tracker.ui.widget.TrackerWidget
import com.brk718.tracker.util.CrashReporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ShipmentRepository,
    private val prefsRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = prefsRepository.preferences.first()
            val activeShipments = repository.activeShipments.first()

            activeShipments.forEach { shipmentWithEvents ->
                val oldStatus = shipmentWithEvents.shipment.status
                try {
                    repository.refreshShipment(shipmentWithEvents.shipment.id)

                    // Leer el nuevo estado tras el refresh
                    val updated = repository.getShipment(shipmentWithEvents.shipment.id).first()
                    val newStatus = updated?.shipment?.status ?: return@forEach

                    // Notificar si el estado cambió y las notificaciones están habilitadas
                    if (newStatus != oldStatus) {
                        // Contar entregas exitosas para el rating dialog
                        if (newStatus.lowercase().contains("entregado")) {
                            prefsRepository.incrementDeliveredCount()
                        }
                        if (prefs.notificationsEnabled && !isInQuietHours(prefs.quietHoursEnabled, prefs.quietHoursStart, prefs.quietHoursEnd)) {
                            val shouldNotify = if (prefs.onlyImportantEvents) {
                                isImportantStatus(newStatus)
                            } else {
                                true
                            }
                            if (shouldNotify) {
                                sendNotification(
                                    title = updated.shipment.title,
                                    newStatus = newStatus,
                                    shipmentId = updated.shipment.id
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Continuar con los demás envíos aunque uno falle
                    CrashReporter.recordException(e)
                }
            }
            // Actualizar el widget con los datos frescos
            updateWidgetIfInstalled()

            Result.success()
        } catch (e: Exception) {
            CrashReporter.recordException(e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun updateWidgetIfInstalled() {
        try {
            val manager = GlanceAppWidgetManager(appContext)
            val glanceIds = manager.getGlanceIds(TrackerWidget::class.java)
            glanceIds.forEach { glanceId ->
                TrackerWidget().update(appContext, glanceId)
            }
        } catch (e: Exception) {
            // El widget puede no estar instalado — ignorar silenciosamente
            android.util.Log.d("SyncWorker", "Widget no instalado o error al actualizar: ${e.message}")
        }
    }

    /**
     * Devuelve true si la hora actual está dentro del rango de silencio.
     * Maneja rangos que cruzan medianoche (ej. 23:00 → 07:00).
     */
    private fun isInQuietHours(enabled: Boolean, startHour: Int, endHour: Int): Boolean {
        if (!enabled) return false
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (startHour <= endHour) {
            // Rango dentro del mismo día (ej. 02:00 → 08:00)
            currentHour in startHour until endHour
        } else {
            // Rango cruzando medianoche (ej. 23:00 → 07:00)
            currentHour >= startHour || currentHour < endHour
        }
    }

    private fun isImportantStatus(status: String): Boolean {
        val lower = status.lowercase()
        return lower.contains("entregado") ||
            lower.contains("en camino") ||
            lower.contains("en tránsito") ||
            lower.contains("en transito") ||
            lower.contains("en reparto") ||
            lower.contains("incidencia") ||
            lower.contains("problema") ||
            lower.contains("intento fallido") ||
            lower.contains("exception")
    }

    private fun sendNotification(title: String, newStatus: String, shipmentId: String) {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("shipmentId", shipmentId)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            shipmentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, TrackerApp.CHANNEL_SHIPMENT_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(newStatus)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(shipmentId.hashCode(), notification)
    }
}
