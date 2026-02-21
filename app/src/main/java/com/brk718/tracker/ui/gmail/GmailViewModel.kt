package com.brk718.tracker.ui.gmail

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.remote.GmailService
import com.brk718.tracker.data.repository.ShipmentRepository
import com.brk718.tracker.domain.ParsedShipment
import com.brk718.tracker.ui.add.FREE_SHIPMENT_LIMIT
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GmailUiState(
    val isConnected: Boolean = false,
    val accountEmail: String? = null,
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val foundShipments: List<ParsedShipment> = emptyList(),
    val importedIds: Set<String> = emptySet(),
    val importingIds: Set<String> = emptySet(), // trackingNumbers que están siendo importados ahora
    val error: String? = null,
    val limitReachedIds: Set<String> = emptySet() // trackingNumbers rechazados por límite free
)

@HiltViewModel
class GmailViewModel @Inject constructor(
    private val gmailService: GmailService,
    private val repository: ShipmentRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GmailUiState())
    val uiState: StateFlow<GmailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val connected = gmailService.isConnected()
            _uiState.update {
                it.copy(
                    isConnected = connected,
                    accountEmail = gmailService.getAccountEmail()
                )
            }
        }
    }

    fun getSignInIntent(): Intent = gmailService.signInIntent

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.result
                gmailService.handleSignInResult(account)
                _uiState.update {
                    it.copy(
                        isConnected = true,
                        accountEmail = account.email,
                        error = null
                    )
                }
                // Escanear automáticamente al conectarse por primera vez
                scanEmails()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Error al conectar: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            gmailService.disconnect()
            _uiState.update { GmailUiState() }
        }
    }

    fun scanEmails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            try {
                val shipments = gmailService.fetchRecentTrackingNumbers()

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
            // Marcar como "importando" para mostrar spinner en el botón
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
