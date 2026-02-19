package com.brk718.tracker.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddShipmentViewModel @Inject constructor(
    private val repository: ShipmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddUiState>(AddUiState.Idle)
    val uiState: StateFlow<AddUiState> = _uiState

    fun addShipment(trackingNumber: String, title: String, manualCarrier: String? = null) {
        viewModelScope.launch {
            _uiState.value = AddUiState.Loading
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
    object Idle : AddUiState()
    object Loading : AddUiState()
    object Success : AddUiState()
    data class Error(val message: String) : AddUiState()
}
