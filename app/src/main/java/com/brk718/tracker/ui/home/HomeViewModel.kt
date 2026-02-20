package com.brk718.tracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
import com.brk718.tracker.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RATING_TRIGGER_COUNT = 3

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ShipmentRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /** true cuando el usuario ha tenido >= RATING_TRIGGER_COUNT entregas exitosas */
    val shouldShowRatingRequest: StateFlow<Boolean> = prefsRepository.preferences
        .map { it.deliveredCount >= RATING_TRIGGER_COUNT }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

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
                // first() garantiza que esperamos la primera emisión del Flow antes de iterar,
                // evitando la race condition de stateIn().value que puede ser lista vacía.
                repository.activeShipments.first().forEach { shipmentWithEvents ->
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

    // ── Selección múltiple (bulk) ─────────────────────────────────────────────
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun toggleSelection(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun selectAll() {
        _selectedIds.value = shipments.value.map { it.shipment.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun archiveSelected() {
        viewModelScope.launch {
            _selectedIds.value.forEach { repository.archiveShipment(it) }
            _selectedIds.value = emptySet()
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            _selectedIds.value.forEach { repository.deleteShipment(it) }
            _selectedIds.value = emptySet()
        }
    }
}
