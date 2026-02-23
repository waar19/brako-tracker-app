package com.brk718.tracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.ads.MobileAds
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
        MobileAds.initialize(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Borrar canal antiguo (IMPORTANCE_DEFAULT no mostraba banners heads-up)
        notificationManager.deleteNotificationChannel(CHANNEL_SHIPMENT_UPDATES_OLD)

        // Canal v2: IMPORTANCE_HIGH garantiza banners emergentes (heads-up) en Android 8+
        val shipmentChannel = NotificationChannel(
            CHANNEL_SHIPMENT_UPDATES,
            "Actualizaciones de envíos",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones cuando cambia el estado de tus envíos"
            enableVibration(true)
        }

        notificationManager.createNotificationChannel(shipmentChannel)
    }

    companion object {
        const val CHANNEL_SHIPMENT_UPDATES = "shipment_updates_v2"
        private const val CHANNEL_SHIPMENT_UPDATES_OLD = "shipment_updates"
    }
}
