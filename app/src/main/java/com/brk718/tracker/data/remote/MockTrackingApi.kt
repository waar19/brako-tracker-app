package com.brk718.tracker.data.remote

import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Mock implementation of TrackingApi for testing without a real backend.
 * Returns simulated AfterShip-style responses.
 */
class MockTrackingApi @Inject constructor() : TrackingApi {

    override suspend fun detectCouriers(body: DetectCourierRequest): DetectCourierResponse {
        delay(500)
        return DetectCourierResponse(
            meta = AfterShipMeta(code = 200, message = "OK"),
            data = DetectCourierData(
                couriers = listOf(DetectedCourier(slug = "mock-courier", name = "Mock Courier"))
            )
        )
    }

    override suspend fun createTracking(body: CreateTrackingRequest): AfterShipResponse {
        delay(500)
        return AfterShipResponse(
            meta = AfterShipMeta(code = 201, message = "Created"),
            data = null
        )
    }

    override suspend fun getTrackingInfo(carrier: String, trackingNumber: String): AfterShipResponse {
        delay(1500) // Simular latencia de red

        return AfterShipResponse(
            meta = AfterShipMeta(code = 200, message = "OK"),
            data = AfterShipData(
                tracking = AfterShipTracking(
                    id = "mock_id_$trackingNumber",
                    tracking_number = trackingNumber,
                    slug = carrier,
                    tag = "InTransit",
                    subtag_message = "En camino a destino",
                    checkpoints = listOf(
                        AfterShipCheckpoint(
                            created_at = "2023-10-27T10:00:00Z",
                            message = "Paquete entregado al transportista",
                            location = "Madrid, ES",
                            tag = "PickedUp",
                            subtag_message = "Recogido"
                        ),
                        AfterShipCheckpoint(
                            created_at = "2023-10-28T08:30:00Z",
                            message = "En procesado en centro de distribución",
                            location = "Barcelona, ES",
                            tag = "InTransit",
                            subtag_message = "En tránsito"
                        ),
                        AfterShipCheckpoint(
                            created_at = "2023-10-29T09:15:00Z",
                            message = "Salida de oficina de cambio",
                            location = "Barcelona, ES",
                            tag = "InTransit",
                            subtag_message = "En tránsito"
                        )
                    )
                )
            )
        )
    }
}
