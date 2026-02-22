package com.brk718.tracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    // Error de refresh — se muestra como snackbar y se limpia tras mostrarse
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    fun clearRefreshError() { _refreshError.value = null }

    // Combina el estado raw con isPremium para aplicar el filtro de historial
    // distinctUntilChanged evita recomposiciones cuando el contenido no cambia
    val uiState: StateFlow<DetailUiState> = combine(
        _rawState,
        prefsRepository.preferences
    ) { state, prefs ->
        if (!prefs.isPremium && state is DetailUiState.Success) {
            val cutoffMs = System.currentTimeMillis() - FREE_HISTORY_DAYS * 24 * 60 * 60 * 1000L
            val allEvents = state.shipment.events
            val filteredEvents = allEvents.filter { it.timestamp >= cutoffMs }
            val hidden = allEvents.size - filteredEvents.size
            val filtered = state.shipment.copy(events = filteredEvents)
            DetailUiState.Success(filtered, historyLimited = true, hiddenEventCount = hidden)
        } else if (state is DetailUiState.Success) {
            state.copy(historyLimited = false, hiddenEventCount = 0)
        } else {
            state
        }
    }.distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState.Loading
    )

    // Job para el colector de getShipment — se cancela antes de relanzar para evitar
    // múltiples colectores activos si loadShipment() se llama más de una vez
    private var loadJob: Job? = null

    init {
        loadShipment()
    }

    fun loadShipment() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
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
                _refreshError.value = "No se pudo actualizar el envío"
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

    fun toggleMute() {
        val current = (uiState.value as? DetailUiState.Success)?.shipment?.shipment ?: return
        viewModelScope.launch {
            if (current.isMuted) repository.unmuteShipment(shipmentId)
            else repository.muteShipment(shipmentId)
        }
    }
}

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(
        val shipment: ShipmentWithEvents,
        val historyLimited: Boolean = false,
        val hiddenEventCount: Int = 0
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
