package com.brk718.tracker.data.repository

import com.brk718.tracker.data.local.ShipmentDao
import com.brk718.tracker.data.local.ShipmentEntity
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.TrackingEventEntity
import com.brk718.tracker.data.remote.CreateTrackingBody
import com.brk718.tracker.data.remote.CreateTrackingRequest
import com.brk718.tracker.data.remote.DetectCourierBody
import com.brk718.tracker.data.remote.DetectCourierRequest
import com.brk718.tracker.data.remote.TrackingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShipmentRepository @Inject constructor(
    private val dao: ShipmentDao,
    private val api: TrackingApi
) {

    val activeShipments: Flow<List<ShipmentWithEvents>> = dao.getAllActiveShipments()

    fun getShipment(id: String): Flow<ShipmentWithEvents?> = dao.getShipmentById(id)

    suspend fun addShipment(trackingNumber: String, carrier: String, title: String) {
        val id = UUID.randomUUID().toString()
        val shipment = ShipmentEntity(
            id = id,
            trackingNumber = trackingNumber,
            carrier = carrier,
            title = title.ifBlank { trackingNumber },
            status = "Registrando...",
            lastUpdate = System.currentTimeMillis()
        )
        dao.insertShipment(shipment)

        withContext(Dispatchers.IO) {
            // Paso 1: Detectar el courier correcto automáticamente
            var detectedSlug = carrier.ifBlank { null }
            try {
                val detectResponse = api.detectCouriers(
                    DetectCourierRequest(
                        tracking = DetectCourierBody(tracking_number = trackingNumber)
                    )
                )
                val couriers = detectResponse.data?.couriers
                if (!couriers.isNullOrEmpty()) {
                    detectedSlug = couriers.first().slug
                    android.util.Log.d("AfterShip", "Courier detectado: ${couriers.first().name} (slug: $detectedSlug)")
                    // Actualizar el carrier en la base de datos local
                    dao.insertShipment(shipment.copy(carrier = detectedSlug, status = "Courier: ${couriers.first().name}"))
                } else {
                    android.util.Log.w("AfterShip", "No se detectó courier para $trackingNumber")
                    dao.insertShipment(shipment.copy(status = "Courier no reconocido"))
                }
            } catch (e: Exception) {
                android.util.Log.e("AfterShip", "Error detectando courier: ${e.message}")
            }

            // Paso 2: Crear el tracking en AfterShip
            if (detectedSlug != null) {
                try {
                    api.createTracking(
                        CreateTrackingRequest(
                            tracking = CreateTrackingBody(
                                tracking_number = trackingNumber,
                                slug = detectedSlug,
                                title = title.ifBlank { null }
                            )
                        )
                    )
                    android.util.Log.d("AfterShip", "Tracking creado con slug: $detectedSlug")
                } catch (e: Exception) {
                    android.util.Log.e("AfterShip", "Error creando tracking: ${e.message}")
                }

                // Paso 3: Consultar el estado
                try {
                    refreshShipment(id)
                } catch (e: Exception) {
                    android.util.Log.e("AfterShip", "Error consultando estado: ${e.message}")
                }
            }
        }
    }

    suspend fun refreshShipment(id: String) = withContext(Dispatchers.IO) {
        val shipmentWithEvents = dao.getShipmentById(id).first() ?: return@withContext
        val shipment = shipmentWithEvents.shipment

        try {
            val response = api.getTrackingInfo(shipment.carrier, shipment.trackingNumber)
            val tracking = response.data?.tracking ?: return@withContext

            // Mapear el tag de AfterShip a un estado legible en español
            val statusText = when (tracking.tag) {
                "Delivered"     -> "Entregado"
                "InTransit"     -> "En Tránsito"
                "OutForDelivery"-> "En reparto"
                "AttemptFail"   -> "Intento fallido"
                "Exception"     -> "Incidencia"
                "Pending"       -> "Pendiente"
                else            -> tracking.subtag_message ?: tracking.tag
            }

            // Actualizar estado del envío
            dao.insertShipment(shipment.copy(
                status = statusText,
                lastUpdate = System.currentTimeMillis()
            ))

            // Mapear checkpoints a eventos locales
            val events = tracking.checkpoints.mapIndexed { index, checkpoint ->
                TrackingEventEntity(
                    id = 0L,
                    shipmentId = id,
                    timestamp = System.currentTimeMillis() - (index * 3600000L),
                    description = checkpoint.message ?: checkpoint.subtag_message ?: "Sin descripción",
                    location = checkpoint.location ?: "",
                    status = checkpoint.tag ?: ""
                )
            }
            dao.clearEventsForShipment(id)
            dao.insertEvents(events)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun archiveShipment(id: String) {
        dao.archiveShipment(id)
    }

    suspend fun deleteShipment(id: String) {
        dao.deleteShipment(id)
    }
}
