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

            // Recopilar todas las actualizaciones antes de notificar
            data class ShipmentUpdate(val title: String, val newStatus: String, val id: String)
            val updates = mutableListOf<ShipmentUpdate>()

            activeShipments.forEach { shipmentWithEvents ->
                val oldStatus = shipmentWithEvents.shipment.status
                try {
                    repository.refreshShipment(shipmentWithEvents.shipment.id)

                    // Leer el nuevo estado tras el refresh
                    val updated = repository.getShipment(shipmentWithEvents.shipment.id).first()
                    val newStatus = updated?.shipment?.status ?: return@forEach

                    // Acumular actualizaciones de estado
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
                                updates.add(ShipmentUpdate(updated.shipment.title, newStatus, updated.shipment.id))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Continuar con los demás envíos aunque uno falle
                    CrashReporter.recordException(e)
                }
            }

            // Enviar notificaciones: individual si hay 1, agrupada si hay 2+
            when {
                updates.size == 1 -> {
                    val u = updates.first()
                    sendIndividualNotification(u.title, u.newStatus, u.id)
                }
                updates.size >= 2 -> {
                    sendGroupedNotification(updates.map { Triple(it.title, it.newStatus, it.id) })
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

    private fun sendIndividualNotification(title: String, newStatus: String, shipmentId: String) {
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
            .setGroup(NOTIFICATION_GROUP_KEY)
            .setNumber(1)
            .build()

        notificationManager.notify(shipmentId.hashCode(), notification)
    }

    private fun sendGroupedNotification(updates: List<Triple<String, String, String>>) {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intento que abre la pantalla principal
        val summaryIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val summaryPendingIntent = PendingIntent.getActivity(
            appContext,
            SUMMARY_NOTIFICATION_ID,
            summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Publicar una notificación individual por envío (parte del grupo)
        updates.forEach { (title, status, id) ->
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("shipmentId", id)
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(appContext, TrackerApp.CHANNEL_SHIPMENT_UPDATES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(status)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .build()
            notificationManager.notify(id.hashCode(), notification)
        }

        // Notificación de resumen (requerida para el grupo en Android)
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("${updates.size} envíos actualizados")
        updates.forEach { (title, status, _) ->
            inboxStyle.addLine("$title — $status")
        }

        val summaryNotification = NotificationCompat.Builder(appContext, TrackerApp.CHANNEL_SHIPMENT_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${updates.size} envíos actualizados")
            .setContentText(updates.joinToString(", ") { it.first })
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(summaryPendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP_KEY)
            .setGroupSummary(true)
            .setNumber(updates.size)
            .build()

        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
    }

    companion object {
        private const val NOTIFICATION_GROUP_KEY = "com.brk718.tracker.SHIPMENT_UPDATES"
        private const val SUMMARY_NOTIFICATION_ID = 0
    }
}
