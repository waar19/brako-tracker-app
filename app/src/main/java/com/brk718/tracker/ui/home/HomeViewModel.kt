package com.brk718.tracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ShipmentRepository
) : ViewModel() {

    // ── Búsqueda ──────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val shipments: StateFlow<List<ShipmentWithEvents>> = combine(
        repository.activeShipments,
        _searchQuery
    ) { all, query ->
        if (query.isBlank()) all
        else {
            val q = query.lowercase().trim()
            all.filter { item ->
                item.shipment.title.lowercase().contains(q) ||
                item.shipment.trackingNumber.lowercase().contains(q) ||
                ShipmentRepository.displayName(item.shipment.carrier).lowercase().contains(q) ||
                item.shipment.status.lowercase().contains(q)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    // ── Refresh ───────────────────────────────────────────────────────────────
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Leer la lista directamente del repositorio para no depender
                // del estado filtrado (que puede estar vacío si hay búsqueda activa)
                repository.activeShipments.stateIn(this).value.forEach { shipmentWithEvents ->
                    repository.refreshShipment(shipmentWithEvents.shipment.id)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun deleteShipment(id: String) {
        viewModelScope.launch {
            repository.deleteShipment(id)
        }
    }

    fun archiveShipment(id: String) {
        viewModelScope.launch {
            repository.archiveShipment(id)
        }
    }
}
