package com.brk718.tracker.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brk718.tracker.MainActivity
import com.brk718.tracker.TrackerApp
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
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
                    if (newStatus != oldStatus && prefs.notificationsEnabled) {
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
                } catch (e: Exception) {
                    // Continuar con los demás envíos aunque uno falle
                }
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(newStatus)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(shipmentId.hashCode(), notification)
    }
}
