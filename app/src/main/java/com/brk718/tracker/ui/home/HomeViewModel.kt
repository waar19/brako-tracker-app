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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ShipmentRepository
) : ViewModel() {

    val shipments: StateFlow<List<ShipmentWithEvents>> = repository.activeShipments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                shipments.value.forEach { shipmentWithEvents ->
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
