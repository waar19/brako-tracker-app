package com.brk718.tracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ShipmentRepository
) : ViewModel() {

    private val shipmentId: String = checkNotNull(savedStateHandle["shipmentId"])
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    // Flag independiente: true mientras se refresca, sin destruir el contenido visible
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadShipment()
    }

    fun loadShipment() {
        viewModelScope.launch {
            repository.getShipment(shipmentId).collect { shipment ->
                if (shipment != null) {
                    _uiState.value = DetailUiState.Success(shipment)
                } else {
                    _uiState.value = DetailUiState.Error("Envío no encontrado")
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refreshShipment(shipmentId)
                // El flow de loadShipment() se actualizará automáticamente desde la DB
            } catch (e: Exception) {
                // No destruir la pantalla con un error de red; solo loguear
                android.util.Log.e("DetailViewModel", "Error al refrescar: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val shipment: ShipmentWithEvents) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
