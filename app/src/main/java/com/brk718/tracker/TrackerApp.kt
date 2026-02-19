package com.brk718.tracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Canal principal: cambios de estado de envíos
        val shipmentChannel = NotificationChannel(
            CHANNEL_SHIPMENT_UPDATES,
            "Actualizaciones de envíos",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificaciones cuando cambia el estado de tus envíos"
            enableVibration(true)
        }

        notificationManager.createNotificationChannel(shipmentChannel)
    }

    companion object {
        const val CHANNEL_SHIPMENT_UPDATES = "shipment_updates"
    }
}
