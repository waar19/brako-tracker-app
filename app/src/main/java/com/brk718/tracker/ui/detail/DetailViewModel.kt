package com.brk718.tracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

// Días de historial disponibles en el plan gratuito
const val FREE_HISTORY_DAYS = 30L

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ShipmentRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val shipmentId: String = checkNotNull(savedStateHandle["shipmentId"])
    private val _rawState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)

    // Flag independiente: true mientras se refresca, sin destruir el contenido visible
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Combina el estado raw con isPremium para aplicar el filtro de historial
    val uiState: StateFlow<DetailUiState> = combine(
        _rawState,
        prefsRepository.preferences
    ) { state, prefs ->
        if (!prefs.isPremium && state is DetailUiState.Success) {
            val cutoffMs = System.currentTimeMillis() - FREE_HISTORY_DAYS * 24 * 60 * 60 * 1000L
            val filtered = state.shipment.copy(
                events = state.shipment.events.filter { it.timestamp >= cutoffMs }
            )
            DetailUiState.Success(filtered, historyLimited = true)
        } else if (state is DetailUiState.Success) {
            state.copy(historyLimited = false)
        } else {
            state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState.Loading
    )

    init {
        loadShipment()
    }

    fun loadShipment() {
        viewModelScope.launch {
            repository.getShipment(shipmentId).collect { shipment ->
                if (shipment != null) {
                    _rawState.value = DetailUiState.Success(shipment)
                } else {
                    _rawState.value = DetailUiState.Error("Envio no encontrado")
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refreshShipment(shipmentId)
            } catch (e: Exception) {
                android.util.Log.e("DetailViewModel", "Error al refrescar: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateTitle(newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.updateTitle(shipmentId, trimmed)
        }
    }
}

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(
        val shipment: ShipmentWithEvents,
        val historyLimited: Boolean = false
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
