package com.brk718.tracker.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

const val FREE_SHIPMENT_LIMIT = 10

@HiltViewModel
class AddShipmentViewModel @Inject constructor(
    private val repository: ShipmentRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddUiState>(AddUiState.Idle)
    val uiState: StateFlow<AddUiState> = _uiState

    fun addShipment(trackingNumber: String, title: String, manualCarrier: String? = null) {
        viewModelScope.launch {
            _uiState.value = AddUiState.Loading

            // Validar límite de envíos activos para usuarios free
            val prefs = prefsRepository.userPreferencesFlow.first()
            if (!prefs.isPremium) {
                val activeCount = repository.activeShipments.first().size
                if (activeCount >= FREE_SHIPMENT_LIMIT) {
                    _uiState.value = AddUiState.LimitReached
                    return@launch
                }
            }

            val carrier = manualCarrier ?: detectCarrier(trackingNumber)

            if (carrier == "Desconocido" && manualCarrier == null) {
                _uiState.value = AddUiState.Error("No se pudo detectar el transportista. Seleccione uno manualmente.")
                return@launch
            }

            try {
                repository.addShipment(trackingNumber, carrier, title.ifBlank { trackingNumber })
                _uiState.value = AddUiState.Success
            } catch (e: Exception) {
                _uiState.value = AddUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = AddUiState.Idle
    }

    private fun detectCarrier(tracking: String): String {
        return when {
            tracking.startsWith("1Z") -> "UPS"
            tracking.length == 12 && tracking.all { it.isDigit() } -> "FedEx" // Simplificado
            tracking.length == 22 && tracking.startsWith("9") -> "USPS"
            tracking.startsWith("TBA") -> "Amazon Logistics"
            else -> "Desconocido"
        }
    }
}

sealed class AddUiState {
    data object Idle : AddUiState()
    data object Loading : AddUiState()
    data object Success : AddUiState()
    data object LimitReached : AddUiState()   // Free tier: límite de envíos activos alcanzado
    data class Error(val message: String) : AddUiState()
}
