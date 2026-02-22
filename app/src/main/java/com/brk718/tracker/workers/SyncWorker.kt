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
import com.brk718.tracker.util.ShipmentStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

            // Refrescar todos los envíos en paralelo (coroutineScope garantiza que se esperan todos)
            coroutineScope {
                activeShipments.map { shipmentWithEvents ->
                    async {
                        val oldStatus = shipmentWithEvents.shipment.status
                        try {
                            repository.refreshShipment(shipmentWithEvents.shipment.id)

                            // Leer el nuevo estado tras el refresh
                            val updated = repository.getShipment(shipmentWithEvents.shipment.id).first()
                            val newStatus = updated?.shipment?.status ?: return@async

                            // Acumular actualizaciones de estado
                            if (newStatus != oldStatus) {
                                // Contar entregas exitosas para el rating dialog
                                if (newStatus.lowercase().contains(ShipmentStatus.DELIVERED)) {
                                    prefsRepository.incrementDeliveredCount()
                                }
                                if (prefs.notificationsEnabled && !isInQuietHours(prefs.quietHoursEnabled, prefs.quietHoursStart, prefs.quietHoursStartMinute, prefs.quietHoursEnd, prefs.quietHoursEndMinute)) {
                                    val shouldNotify = if (prefs.onlyImportantEvents) {
                                        isImportantStatus(newStatus)
                                    } else {
                                        true
                                    }
                                    if (shouldNotify) {
                                        synchronized(updates) {
                                            updates.add(ShipmentUpdate(updated.shipment.title, newStatus, updated.shipment.id))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Continuar con los demás envíos aunque uno falle
                            CrashReporter.recordException(e)
                        }
                    }
                }.awaitAll()
            }

            // Verificar que el sistema permite notificaciones antes de intentar enviarlas
            if (!androidx.core.app.NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
                android.util.Log.w("SyncWorker", "Notificaciones bloqueadas a nivel sistema — saltando envío")
                updateWidgetIfInstalled()
                return Result.success()
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

            // Registrar el timestamp del último sync exitoso
            prefsRepository.setLastSyncTimestamp(System.currentTimeMillis())

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
    private fun isInQuietHours(enabled: Boolean, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): Boolean {
        if (!enabled) return false
        val cal = Calendar.getInstance()
        val currentMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMin   = startHour * 60 + startMinute
        val endMin     = endHour   * 60 + endMinute
        return if (startMin <= endMin) {
            // Rango dentro del mismo día (ej. 02:30 → 08:00)
            currentMin in startMin until endMin
        } else {
            // Rango cruzando medianoche (ej. 23:30 → 07:00)
            currentMin >= startMin || currentMin < endMin
        }
    }

    private fun isImportantStatus(status: String): Boolean {
        val lower = status.lowercase()
        return lower.contains(ShipmentStatus.DELIVERED) ||
            lower.contains(ShipmentStatus.ON_THE_WAY) ||
            lower.contains(ShipmentStatus.IN_TRANSIT) ||
            lower.contains(ShipmentStatus.IN_TRANSIT_ALT) ||
            lower.contains(ShipmentStatus.OUT_FOR_DELIVERY) ||
            lower.contains(ShipmentStatus.EXCEPTION) ||
            lower.contains(ShipmentStatus.PROBLEM) ||
            lower.contains(ShipmentStatus.FAILED_ATTEMPT) ||
            lower.contains("exception")   // status de AfterShip en inglés
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
                .setPriority(NotificationCompat.PRIORITY_HIGH)
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
