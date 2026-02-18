package com.brk718.tracker.data.remote

import com.brk718.tracker.domain.ParsedShipment

interface EmailService {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun isConnected(): Boolean
    suspend fun fetchRecentTrackingNumbers(): List<ParsedShipment>
}
