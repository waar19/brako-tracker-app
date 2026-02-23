package com.brk718.tracker.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.brk718.tracker.workers.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * Re-encola el SyncWorker de WorkManager tras un reinicio del dispositivo o
 * actualización de la app. Actúa como seguro frente a OEMs (Samsung One UI,
 * Xiaomi MIUI, etc.) que interfieren con el reschedule automático de JobScheduler.
 *
 * Usa ExistingPeriodicWorkPolicy.KEEP para no interrumpir si WorkManager ya
 * tiene el job programado correctamente (comportamiento normal en Android puro).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        // Re-encolar con KEEP: no-op si WorkManager ya tiene el job programado.
        // Usa 2 h como intervalo seguro; el usuario puede ajustarlo en Settings.
        val request = PeriodicWorkRequestBuilder<SyncWorker>(2L, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("TrackerSync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
