package com.brk718.tracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.brk718.tracker.BuildConfig
import com.brk718.tracker.data.billing.BillingRepository
import com.brk718.tracker.data.billing.BillingState
import com.brk718.tracker.data.local.AmazonSessionManager
import com.brk718.tracker.data.local.UserPreferences
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
import com.brk718.tracker.util.CsvExporter
import com.brk718.tracker.workers.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val preferencesLoaded: Boolean = false,
    val isAmazonConnected: Boolean = false,
    val appVersion: String = "",
    val cacheCleared: Boolean = false,
    val lastSyncText: String = "Nunca",
    val subscriptionPriceText: String = "—",
    val billingState: BillingState = BillingState.Idle
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: UserPreferencesRepository,
    private val amazonSessionManager: AmazonSessionManager,
    private val billingRepository: BillingRepository,
    private val shipmentRepository: ShipmentRepository
) : ViewModel() {

    init {
        // Backfill de estadísticas desde Room si los contadores de DataStore están en 0
        viewModelScope.launch {
            val total = shipmentRepository.countAllShipments()
            val delivered = shipmentRepository.countDeliveredShipments()
            prefsRepo.syncStatsFromRoom(total, delivered)
        }

        // Al perder premium, re-programar el worker con el intervalo free (ya reseteado a 2h)
        viewModelScope.launch {
            prefsRepo.preferences
                .map { it.isPremium }
                .distinctUntilChanged()
                .drop(1)   // ignorar el valor inicial al arrancar la app
                .collect { isPremium ->
                    if (!isPremium) {
                        val prefs = prefsRepo.preferences.first()
                        if (prefs.autoSync) scheduleSyncWorker(prefs.syncIntervalHours, prefs.syncOnlyOnWifi)
                    }
                }
        }
    }

    // Uri del CSV exportado (null = sin exportar, Unit = exportado, String = error)
    private val _exportResult = MutableStateFlow<ExportResult>(ExportResult.Idle)
    val exportResult: StateFlow<ExportResult> = _exportResult.asStateFlow()

    // Combina el trabajo periódico ("TrackerSync") y el manual ("TrackerSyncNow")
    // para mostrar el estado más reciente/relevante en Settings.
    private val workManager = WorkManager.getInstance(context)
    private val periodicSyncFlow = workManager.getWorkInfosForUniqueWorkFlow("TrackerSync")
    private val oneTimeSyncFlow  = workManager.getWorkInfosForUniqueWorkFlow("TrackerSyncNow")

    // Emite el WorkInfo más representativo: prefiere RUNNING > ENQUEUED > el más reciente entre ambos
    private val syncWorkInfoFlow = combine(periodicSyncFlow, oneTimeSyncFlow) { periodic, oneTime ->
        val all = (periodic + oneTime).filter { it.state != WorkInfo.State.CANCELLED }
        // RUNNING tiene prioridad (muestra "Sincronizando ahora...")
        all.firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?: all.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
            ?: all.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
            ?: all.firstOrNull { it.state == WorkInfo.State.FAILED }
            ?: all.firstOrNull()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsRepo.preferences,
        syncWorkInfoFlow,
        billingRepository.productDetails,
        billingRepository.billingState
    ) { prefs, workInfo, productDetails, billingState ->
        val lastSyncText = workInfo?.let { formatLastSync(it) } ?: "Nunca"
        SettingsUiState(
            preferences = prefs,
            preferencesLoaded = true,
            isAmazonConnected = amazonSessionManager.isLoggedIn(),
            appVersion = BuildConfig.VERSION_NAME,
            cacheCleared = false,
            lastSyncText = lastSyncText,
            subscriptionPriceText = billingRepository.getPriceText(),
            billingState = billingState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            isAmazonConnected = amazonSessionManager.isLoggedIn(),
            appVersion = BuildConfig.VERSION_NAME
        )
    )

    private fun formatLastSync(workInfo: WorkInfo): String {
        // WorkManager no expone directamente el último tiempo de ejecución en la API pública,
        // pero podemos usar el estado para dar retroalimentación útil
        return when (workInfo.state) {
            WorkInfo.State.RUNNING   -> "Sincronizando ahora..."
            WorkInfo.State.ENQUEUED  -> "En cola"
            WorkInfo.State.SUCCEEDED -> "Completado recientemente"
            WorkInfo.State.FAILED    -> "Falló la última sincronización"
            WorkInfo.State.CANCELLED -> "Cancelada"
            WorkInfo.State.BLOCKED   -> "Esperando condiciones"
            else                     -> "Desconocido"
        }
    }

    // === Notificaciones ===
    fun setNotificationsEnabled(value: Boolean) = viewModelScope.launch {
        prefsRepo.setNotificationsEnabled(value)
    }

    fun setOnlyImportantEvents(value: Boolean) = viewModelScope.launch {
        prefsRepo.setOnlyImportantEvents(value)
    }

    fun setQuietHoursEnabled(value: Boolean) = viewModelScope.launch {
        prefsRepo.setQuietHoursEnabled(value)
    }

    fun setQuietHoursStart(hour: Int) = viewModelScope.launch {
        prefsRepo.setQuietHoursStart(hour)
    }

    fun setQuietHoursEnd(hour: Int) = viewModelScope.launch {
        prefsRepo.setQuietHoursEnd(hour)
    }

    fun syncNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // OneTime work para sincronización inmediata (visible en syncWorkInfoFlow)
        val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5L, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(
            "TrackerSyncNow",
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )

        // Re-encolar el trabajo periódico con UPDATE para resetear su estado FAILED
        val prefs = uiState.value.preferences
        if (prefs.autoSync) {
            scheduleSyncWorker(prefs.syncIntervalHours, prefs.syncOnlyOnWifi)
        }
    }

    // === Sincronización ===
    fun setAutoSync(value: Boolean) = viewModelScope.launch {
        prefsRepo.setAutoSync(value)
        if (value) {
            scheduleSyncWorker(uiState.value.preferences.syncIntervalHours, uiState.value.preferences.syncOnlyOnWifi)
        } else {
            workManager.cancelUniqueWork("TrackerSync")
        }
    }

    fun setSyncIntervalHours(hours: Int) = viewModelScope.launch {
        prefsRepo.setSyncIntervalHours(hours)
        if (uiState.value.preferences.autoSync) {
            scheduleSyncWorker(hours, uiState.value.preferences.syncOnlyOnWifi)
        }
    }

    fun setSyncOnlyOnWifi(value: Boolean) = viewModelScope.launch {
        prefsRepo.setSyncOnlyOnWifi(value)
        if (uiState.value.preferences.autoSync) {
            scheduleSyncWorker(uiState.value.preferences.syncIntervalHours, value)
        }
    }

    // === Apariencia ===
    fun setTheme(theme: String) = viewModelScope.launch {
        prefsRepo.setTheme(theme)
    }

    // === Amazon ===
    fun disconnectAmazon() {
        amazonSessionManager.clearSession()
    }

    // === Suscripción Premium ===
    fun purchaseSubscription(activity: android.app.Activity) {
        billingRepository.purchaseSubscription(activity)
    }

    fun restorePurchases() {
        billingRepository.restorePurchases()
    }

    // === Exportar CSV (solo premium) ===
    fun exportCsv() = viewModelScope.launch {
        _exportResult.value = ExportResult.Loading
        try {
            val active   = shipmentRepository.activeShipments.first()
            val archived = shipmentRepository.archivedShipments.first()
            val all      = active + archived
            val uri = CsvExporter.exportToCsv(context, all)
            _exportResult.value = if (uri != null) ExportResult.Success(uri)
                                  else ExportResult.Error("No se pudo crear el archivo")
        } catch (e: Exception) {
            _exportResult.value = ExportResult.Error(e.message ?: "Error desconocido")
        }
    }

    fun clearExportResult() { _exportResult.value = ExportResult.Idle }

    // === Caché de mapas ===
    fun clearMapCache() {
        try {
            val osmdroidCache = Configuration.getInstance().osmdroidTileCache
            osmdroidCache.deleteRecursively()
        } catch (e: Exception) {
            // ignorar si falla
        }
    }

    // === Debug (solo visible en builds DEBUG) ===
    fun setIsPremiumDebug(value: Boolean) = viewModelScope.launch {
        prefsRepo.setIsPremium(value)
    }

    // === Helpers ===
    private fun scheduleSyncWorker(intervalHours: Int, onlyWifi: Boolean) {
        // Guard defensivo: si el usuario no es premium, nunca encolar worker de 30 min
        val isPremium = uiState.value.preferences.isPremium
        val effectiveInterval = if (intervalHours == -1 && !isPremium) 2 else intervalHours
        if (effectiveInterval == 0) return  // manual — no programar
        val networkType = if (onlyWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder().setRequiredNetworkType(networkType).build()
        // -1 = 30 minutos (premium). WorkManager requiere mínimo 15 min en modo periódico.
        val request = if (effectiveInterval == -1) {
            PeriodicWorkRequestBuilder<SyncWorker>(30L, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5L, TimeUnit.MINUTES)
                .build()
        } else {
            PeriodicWorkRequestBuilder<SyncWorker>(effectiveInterval.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.MINUTES)
                .build()
        }
        workManager.enqueueUniquePeriodicWork(
            "TrackerSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

sealed class ExportResult {
    data object Idle    : ExportResult()
    data object Loading : ExportResult()
    data class Success(val uri: Uri) : ExportResult()
    data class Error(val message: String) : ExportResult()
}
