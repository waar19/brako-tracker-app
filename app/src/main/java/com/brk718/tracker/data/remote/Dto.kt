package com.brk718.tracker.data.remote

data class TrackingResponse(
    val trackingNumber: String,
    val carrier: String,
    val status: String,
    val events: List<TrackingEventDto>
)

data class TrackingEventDto(
    val timestamp: String, // ISO8601 or similar
    val description: String,
    val location: String?,
    val statusCode: String
)
