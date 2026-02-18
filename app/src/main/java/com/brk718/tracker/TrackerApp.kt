package com.brk718.tracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.brk718.tracker.workers.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
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

        // TODO: Re-habilitar cuando se resuelva el conflicto de WorkManager factory
        // El SyncWorker requiere que WorkManager use HiltWorkerFactory, pero hay un job
        // encolado de sesiones anteriores que usa la factory por defecto.
        // Solución: borrar datos de la app en el dispositivo (Settings → Apps → Tracker → Clear Data)
        // val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(4, TimeUnit.HOURS).build()
        // WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        //     "sync_shipments", ExistingPeriodicWorkPolicy.KEEP, syncRequest
        // )
    }
}
