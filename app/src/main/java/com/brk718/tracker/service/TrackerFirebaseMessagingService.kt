package com.brk718.tracker.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.brk718.tracker.MainActivity
import com.brk718.tracker.R
import com.brk718.tracker.TrackerApp
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Servicio FCM para recibir push notifications desde el backend.
 *
 * Payload esperado (data message):
 *   - title      : título de la notificación
 *   - body       : cuerpo de la notificación
 *   - shipmentId : (opcional) ID del envío para abrir el detalle al tocar
 *
 * NOTA: Sin un backend que envíe mensajes a la FCM API, las notificaciones
 * push no llegan automáticamente. Este servicio deja la infraestructura lista
 * para cuando se integre un servidor.
 */
@AndroidEntryPoint
class TrackerFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var prefsRepository: UserPreferencesRepository

    // Scope para corrutinas del service (se cancela al destruir el service)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Se invoca cuando FCM asigna o renueva el token del dispositivo.
     * Guardamos el token en DataStore para enviarlo al backend cuando esté disponible.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            prefsRepository.setFcmToken(token)
        }
    }

    /**
     * Se invoca cuando la app está en primer plano y llega un mensaje de FCM.
     * Para mensajes con `notification` el sistema los muestra automáticamente
     * cuando la app está en background; este método los muestra en foreground.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: return
        val body  = message.notification?.body  ?: message.data["body"]  ?: return
        val shipmentId = message.data["shipmentId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            shipmentId?.let { putExtra("shipmentId", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            shipmentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TrackerApp.CHANNEL_SHIPMENT_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(shipmentId.hashCode(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
