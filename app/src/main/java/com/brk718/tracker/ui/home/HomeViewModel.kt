package com.brk718.tracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.BuildConfig
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
import com.brk718.tracker.util.NetworkMonitor
import com.brk718.tracker.util.ShipmentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    // ── Filtro por estado ─────────────────────────────────────────────────────
    // null = sin filtro; valor = substring a buscar en status (lowercase)
    private val _activeStatusFilter = MutableStateFlow<String?>(null)
    val activeStatusFilter: StateFlow<String?> = _activeStatusFilter.asStateFlow()

    fun setStatusFilter(filter: String?) {
        _activeStatusFilter.value = if (_activeStatusFilter.value == filter) null else filter
    }

    val shipments: StateFlow<List<ShipmentWithEvents>> = combine(
        repository.activeShipments,
        _searchQuery,
        _activeStatusFilter
    ) { all, query, statusFilter ->
        var result = if (query.isBlank()) all
        else {
            val q = query.lowercase().trim()
            all.filter { item ->
                item.shipment.title.lowercase().contains(q) ||
                item.shipment.trackingNumber.lowercase().contains(q) ||
                ShipmentRepository.displayName(item.shipment.carrier).lowercase().contains(q) ||
                item.shipment.status.lowercase().contains(q)
            }
        }
        if (statusFilter != null) {
            result = result.filter { it.shipment.status.lowercase().contains(statusFilter) }
        }
        result
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

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    fun clearRefreshError() { _refreshError.value = null }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshError.value = null
            try {
                val shipments = repository.activeShipments.first()
                // Refrescar todos en paralelo — cada async devuelve true si falló
                val failCount = coroutineScope {
                    shipments.map { shipmentWithEvents ->
                        async {
                            try {
                                repository.refreshShipment(shipmentWithEvents.shipment.id)
                                false // sin fallo
                            } catch (e: Exception) {
                                true  // fallo
                            }
                        }
                    }.awaitAll().count { it }
                }
                if (failCount > 0) {
                    _refreshError.value = if (failCount == 1)
                        "No se pudo actualizar 1 envío"
                    else
                        "No se pudieron actualizar $failCount envíos"
                }
            } catch (e: Exception) {
                _refreshError.value = "Error al actualizar: sin conexión"
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

    // ── What's New ────────────────────────────────────────────────────────────
    /** true si el usuario ya completó el onboarding y hay una versión nueva que no ha visto */
    val showWhatsNew: StateFlow<Boolean> = prefsRepository.preferences
        .map { prefs ->
            prefs.onboardingDone && prefs.lastSeenVersionCode < BuildConfig.VERSION_CODE
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun dismissWhatsNew() {
        viewModelScope.launch {
            prefsRepository.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
        }
    }
}
