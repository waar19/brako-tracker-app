package com.brk718.tracker.data.remote

import com.brk718.tracker.domain.ParsedShipment
import kotlinx.coroutines.delay
import javax.inject.Inject

class GmailServiceMock @Inject constructor() : EmailService {

    private var _isConnected = false

    override suspend fun connect() {
        delay(1000)
        _isConnected = true
    }

    override suspend fun disconnect() {
        _isConnected = false
    }

    override suspend fun isConnected(): Boolean = _isConnected

    override suspend fun fetchRecentTrackingNumbers(): List<ParsedShipment> {
        if (!_isConnected) return emptyList()
        delay(1500) // Simular red
        return listOf(
            ParsedShipment("1Z9999999999999999", "UPS"),
            ParsedShipment("TBA123456789012", "Amazon Logistics"),
            ParsedShipment("123456789012", "FedEx")
        )
    }
}
