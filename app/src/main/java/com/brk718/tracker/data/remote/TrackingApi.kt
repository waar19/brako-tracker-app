package com.brk718.tracker.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TrackingApi {
    // Crear un tracking en AfterShip (requerido antes de poder consultarlo)
    @POST("trackings")
    suspend fun createTracking(@Body body: CreateTrackingRequest): AfterShipResponse

    // Consultar el estado de un tracking existente
    @GET("trackings/{slug}/{tracking_number}")
    suspend fun getTrackingInfo(
        @Path("slug") carrier: String,
        @Path("tracking_number") trackingNumber: String
    ): AfterShipResponse
}

// Request body para crear un tracking
data class CreateTrackingRequest(
    val tracking: CreateTrackingBody
)

data class CreateTrackingBody(
    val tracking_number: String,
    val slug: String? = null,
    val title: String? = null
)

// AfterShip response models
data class AfterShipResponse(
    val meta: AfterShipMeta,
    val data: AfterShipData?
)

data class AfterShipMeta(
    val code: Int,
    val message: String?
)

data class AfterShipData(
    val tracking: AfterShipTracking
)

data class AfterShipTracking(
    val id: String,
    val tracking_number: String,
    val slug: String,
    val tag: String,           // Estado normalizado: "InTransit", "Delivered", etc.
    val subtag_message: String?,
    val checkpoints: List<AfterShipCheckpoint>
)

data class AfterShipCheckpoint(
    val created_at: String?,
    val message: String?,
    val location: String?,
    val tag: String?,
    val subtag_message: String?
)
