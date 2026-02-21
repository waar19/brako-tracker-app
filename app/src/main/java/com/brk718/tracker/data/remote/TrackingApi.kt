package com.brk718.tracker.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TrackingApi {
    // Detectar el courier correcto por número de tracking
    @POST("couriers/detect")
    suspend fun detectCouriers(@Body body: DetectCourierRequest): DetectCourierResponse

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

// Request para detectar el courier
data class DetectCourierRequest(
    val tracking: DetectCourierBody
)

data class DetectCourierBody(
    val tracking_number: String
)

// Response de detección de courier
data class DetectCourierResponse(
    val meta: AfterShipMeta,
    val data: DetectCourierData?
)

data class DetectCourierData(
    val couriers: List<DetectedCourier>?
)

data class DetectedCourier(
    val slug: String,
    val name: String
)

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
    val checkpoints: List<AfterShipCheckpoint>,
    val expected_delivery: String? = null // ISO 8601 fecha estimada de entrega
)

data class AfterShipCheckpoint(
    val created_at: String?,
    val message: String?,
    val location: String?,
    val tag: String?,
    val subtag_message: String?
)
