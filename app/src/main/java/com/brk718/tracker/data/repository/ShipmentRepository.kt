package com.brk718.tracker.data.repository

import com.brk718.tracker.data.local.ShipmentDao
import com.brk718.tracker.data.local.ShipmentEntity
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.TrackingEventEntity
import com.brk718.tracker.data.remote.CreateTrackingBody
import com.brk718.tracker.data.remote.CreateTrackingRequest
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
            try {
                // Paso 1: Registrar el tracking en AfterShip
                // Si el slug está vacío, AfterShip intentará detectar el carrier automáticamente
                val createBody = if (carrier.isBlank()) {
                    CreateTrackingBody(
                        tracking_number = trackingNumber,
                        title = title.ifBlank { null }
                    )
                } else {
                    CreateTrackingBody(
                        tracking_number = trackingNumber,
                        slug = carrier,
                        title = title.ifBlank { null }
                    )
                }
                val createResponse = api.createTracking(CreateTrackingRequest(tracking = createBody))
                android.util.Log.d("AfterShip", "POST response: code=${createResponse.meta.code} msg=${createResponse.meta.message}")
            } catch (e: Exception) {
                android.util.Log.e("AfterShip", "POST error: ${e.message}")
                e.printStackTrace()
            }
            // Paso 2: Consultar el estado actual
            try {
                refreshShipment(id)
            } catch (e: Exception) {
                e.printStackTrace()
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
