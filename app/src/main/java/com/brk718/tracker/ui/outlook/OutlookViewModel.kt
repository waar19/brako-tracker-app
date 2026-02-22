package com.brk718.tracker.ui.outlook

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.remote.OutlookService
import com.brk718.tracker.data.repository.ShipmentRepository
import com.brk718.tracker.domain.ParsedShipment
import com.brk718.tracker.ui.add.FREE_SHIPMENT_LIMIT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutlookUiState(
    val isConnected: Boolean = false,
    val accountEmail: String? = null,
    val isConnecting: Boolean = false,   // true mientras dura el flujo OAuth del browser
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val foundShipments: List<ParsedShipment> = emptyList(),
    val importedIds: Set<String> = emptySet(),
    val importingIds: Set<String> = emptySet(),
    val error: String? = null,
    val limitReachedIds: Set<String> = emptySet()
)

@HiltViewModel
class OutlookViewModel @Inject constructor(
    private val outlookService: OutlookService,
    private val repository: ShipmentRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutlookUiState())
    val uiState: StateFlow<OutlookUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val connected = outlookService.isConnected()
            _uiState.update {
                it.copy(
                    isConnected = connected,
                    accountEmail = outlookService.getAccountEmail()
                )
            }
        }
    }

    /**
     * Lanza el flujo OAuth interactivo de Microsoft.
     * Requiere la Activity activa (obtenida con LocalContext.current en el Composable).
     */
    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            try {
                val ok = outlookService.signIn(activity)
                if (ok) {
                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            accountEmail = outlookService.getAccountEmail(),
                            isConnecting = false
                        )
                    }
                    // Escanear automáticamente al conectarse
                    scanEmails()
                } else {
                    // Usuario canceló el flujo
                    _uiState.update { it.copy(isConnecting = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        error = "Error al conectar: ${e.message}"
                    )
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            outlookService.disconnect()
            _uiState.update { OutlookUiState() }
        }
    }

    fun scanEmails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            try {
                val shipments = outlookService.fetchRecentTrackingNumbers()

                // Pre-marcar como importados los que ya existen en la BD (activos + archivados)
                val allSaved = repository.allShipments.first()
                val savedTrackingNumbers = allSaved.map { it.shipment.trackingNumber.trim() }.toSet()
                val alreadyImported = shipments
                    .map { it.trackingNumber.trim() }
                    .filter { it in savedTrackingNumbers }
                    .toSet()

                _uiState.update {
                    it.copy(
                        isScanning = false,
                        hasScanned = true,
                        foundShipments = shipments,
                        importedIds = it.importedIds + alreadyImported
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        hasScanned = true,
                        error = "Error escaneando: ${e.message}"
                    )
                }
            }
        }
    }

    fun importShipment(shipment: ParsedShipment) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(importingIds = it.importingIds + shipment.trackingNumber, error = null)
            }
            try {
                // Validar límite de envíos activos para usuarios free
                val prefs = prefsRepository.userPreferencesFlow.first()
                if (!prefs.isPremium) {
                    val activeCount = repository.activeShipments.first().size
                    if (activeCount >= FREE_SHIPMENT_LIMIT) {
                        _uiState.update {
                            it.copy(
                                importingIds = it.importingIds - shipment.trackingNumber,
                                limitReachedIds = it.limitReachedIds + shipment.trackingNumber
                            )
                        }
                        return@launch
                    }
                }
                repository.addShipment(
                    trackingNumber = shipment.trackingNumber,
                    carrier = shipment.carrier,
                    title = shipment.title ?: shipment.trackingNumber
                )
                _uiState.update {
                    it.copy(
                        importedIds = it.importedIds + shipment.trackingNumber,
                        importingIds = it.importingIds - shipment.trackingNumber
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        importingIds = it.importingIds - shipment.trackingNumber,
                        error = "Error importando: ${e.message}"
                    )
                }
            }
        }
    }

    /** Importa todos los envíos encontrados que aún no han sido importados */
    fun importAll() {
        val pending = _uiState.value.foundShipments
            .filter { it.trackingNumber !in _uiState.value.importedIds }
        pending.forEach { importShipment(it) }
    }
}
