package com.brk718.tracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun refreshAll() {
        viewModelScope.launch {
            // Lógica para iterar y refrescar todos
            // shipments.value.forEach { repository.refreshShipment(it.shipment.id) }
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
