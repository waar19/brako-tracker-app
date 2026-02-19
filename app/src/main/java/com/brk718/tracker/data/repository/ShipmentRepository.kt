package com.brk718.tracker.data.repository

import com.brk718.tracker.data.local.ShipmentDao
import com.brk718.tracker.data.local.ShipmentEntity
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.TrackingEventEntity
import com.brk718.tracker.data.remote.AmazonTrackingService
import com.brk718.tracker.data.remote.CreateTrackingBody
import com.brk718.tracker.data.remote.CreateTrackingRequest
import com.brk718.tracker.data.remote.DetectCourierBody
import com.brk718.tracker.data.remote.DetectCourierRequest
import com.brk718.tracker.data.remote.TrackingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShipmentRepository @Inject constructor(
    private val dao: ShipmentDao,
    private val api: TrackingApi,
    private val amazonService: AmazonTrackingService
) {
    companion object {
        // Mapeo conocido: nombre del carrier → slug de AfterShip
        // AfterShip se encarga del scraping (incluyendo CAPTCHAs)
        private val CARRIER_SLUG_MAP = mapOf(
            // Carriers colombianos
            "coordinadora" to "coordinadora",
            "servientrega" to "servientrega",
            "inter rapidísimo" to "inter-rapidisimo",
            "inter rapidisimo" to "inter-rapidisimo",
            "interrapidisimo" to "inter-rapidisimo",
            "deprisa" to "deprisa",
            "envía / colvanes" to "envia-co",
            "envía" to "envia-co",
            "envia" to "envia-co",
            "colvanes" to "envia-co",
            "tcc" to "tcc-co",
            "clicoh" to "logysto",
            "logysto" to "logysto",
            "saferbo" to "saferbo",
            "472" to "472-co",
            // PASAREX = Amazon last-mile Colombia → se trackea vía Amazon
            "pasarex" to "amazon",
            "amazon / pasarex" to "amazon",
            // Internacionales
            "fedex" to "fedex",
            "ups" to "ups",
            "usps" to "usps",
            "dhl" to "dhl",
            "dhl express" to "dhl",
            "amazon" to "amazon",
            "amazon logistics" to "amazon",
        )

        /**
         * Resuelve el slug de AfterShip a partir del nombre del carrier
         */
        fun resolveSlug(carrier: String): String? {
            val normalized = carrier.lowercase().trim()
            return CARRIER_SLUG_MAP[normalized]
        }
    }

    val activeShipments: Flow<List<ShipmentWithEvents>> = dao.getAllActiveShipments()

    fun getShipment(id: String): Flow<ShipmentWithEvents?> = dao.getShipmentById(id)

    suspend fun addShipment(trackingNumber: String, carrier: String, title: String) {
        val id = UUID.randomUUID().toString()
        val shipment = ShipmentEntity(
            id = id,
            trackingNumber = trackingNumber,
            carrier = carrier.ifBlank { "manual" },
            title = title.ifBlank { trackingNumber },
            status = "Registrando...",
            lastUpdate = System.currentTimeMillis()
        )
        dao.insertShipment(shipment)

        withContext(Dispatchers.IO) {
            // Paso 1: Determinar el slug del carrier
            var slug: String? = resolveSlug(carrier) // Intentar mapeo directo primero

            // Si no hay mapeo directo, intentar auto-detect de AfterShip
            if (slug == null) {
                try {
                    val detectResponse = api.detectCouriers(
                        DetectCourierRequest(
                            tracking = DetectCourierBody(tracking_number = trackingNumber)
                        )
                    )
                    val couriers = detectResponse.data?.couriers
                    if (!couriers.isNullOrEmpty()) {
                        slug = couriers.first().slug
                        android.util.Log.d("AfterShip", "Auto-detectado: ${couriers.first().name} ($slug)")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AfterShip", "Auto-detect falló: ${e.message}")
                }
            } else {
                android.util.Log.d("AfterShip", "Slug mapeado: $carrier → $slug")
            }

            // Paso 2: Si tenemos slug, crear tracking en AfterShip
            var afterShipOk = false
            if (slug != null && slug != "amazon") {
                try {
                    api.createTracking(
                        CreateTrackingRequest(
                            tracking = CreateTrackingBody(
                                tracking_number = trackingNumber,
                                slug = slug,
                                title = title.ifBlank { null }
                            )
                        )
                    )
                    afterShipOk = true
                    dao.insertShipment(shipment.copy(carrier = slug))
                    android.util.Log.d("AfterShip", "Tracking creado: $slug/$trackingNumber")
                } catch (e: Exception) {
                    android.util.Log.w("AfterShip", "Create tracking falló: ${e.message}")
                }
            }

            // Paso 3: Consultar estado según el método que funcionó
            if (afterShipOk) {
                try {
                    refreshShipment(id)
                } catch (e: Exception) {
                    dao.insertShipment(shipment.copy(
                        carrier = slug ?: carrier,
                        status = "Registrado (sin datos aún)"
                    ))
                }
            } else if (slug == "amazon" || amazonService.isAmazonTracking(trackingNumber)) {
                android.util.Log.d("Tracking", "Usando Amazon tracking para $trackingNumber")
                refreshShipmentAmazon(id)
            } else {
                dao.insertShipment(shipment.copy(
                    carrier = carrier.ifBlank { "manual" },
                    status = "Seguimiento manual"
                ))
            }
        }
    }

    suspend fun refreshShipment(id: String) = withContext(Dispatchers.IO) {
        val shipmentWithEvents = dao.getShipmentById(id).first() ?: return@withContext
        val shipment = shipmentWithEvents.shipment

        // Si es un tracking de Amazon, usar el servicio de Amazon directamente
        if (amazonService.isAmazonTracking(shipment.trackingNumber)) {
            refreshShipmentAmazon(id)
            return@withContext
        }

        try {
            val response = api.getTrackingInfo(shipment.carrier, shipment.trackingNumber)
            val tracking = response.data?.tracking ?: return@withContext

            val statusText = when (tracking.tag) {
                "Delivered"     -> "Entregado"
                "InTransit"     -> "En Tránsito"
                "OutForDelivery"-> "En reparto"
                "AttemptFail"   -> "Intento fallido"
                "Exception"     -> "Incidencia"
                "Pending"       -> "Pendiente"
                else            -> tracking.subtag_message ?: tracking.tag
            }

            dao.insertShipment(shipment.copy(
                status = statusText,
                lastUpdate = System.currentTimeMillis()
            ))

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

    private suspend fun refreshShipmentAmazon(id: String) = withContext(Dispatchers.IO) {
        val shipmentWithEvents = dao.getShipmentById(id).first() ?: return@withContext
        val shipment = shipmentWithEvents.shipment

        try {
            val result = amazonService.getTracking(shipment.trackingNumber)

            if (result.error == "LOGIN_REQUIRED") {
                android.util.Log.w("AmazonTracking", "Requiere login")
                dao.insertShipment(shipment.copy(
                    carrier = "Amazon",
                    status = "LOGIN_REQUIRED",
                    lastUpdate = System.currentTimeMillis()
                ))
                return@withContext
            }

            if (result.error != null) {
                android.util.Log.w("AmazonTracking", "Error: ${result.error}")
                dao.insertShipment(shipment.copy(
                    carrier = "Amazon",
                    status = "Seguimiento manual",
                    lastUpdate = System.currentTimeMillis()
                ))
                return@withContext
            }

            // Actualizar estado
            val status = result.status ?: "En seguimiento"
            dao.insertShipment(shipment.copy(
                carrier = "Amazon",
                status = status,
                lastUpdate = System.currentTimeMillis()
            ))

            // Guardar eventos
            if (result.events.isNotEmpty()) {
                val events = result.events.mapIndexed { index, event ->
                    // Intentar parsear la fecha real
                    val realTimestamp = parseAmazonDate(event.timestamp)
                    // Fallback: Si falla, usar tiempo actual menos un offset para mantener orden relativo
                    val finalTimestamp = realTimestamp ?: (System.currentTimeMillis() - (index * 3600000L))
                    
                    TrackingEventEntity(
                        id = 0L,
                        shipmentId = id,
                        timestamp = finalTimestamp,
                        description = event.description,
                        location = event.location,
                        status = "",
                        latitude = event.latitude,
                        longitude = event.longitude
                    )
                }
                dao.clearEventsForShipment(id)
                dao.insertEvents(events)
            }

            android.util.Log.d("AmazonTracking", "Estado: $status, Eventos: ${result.events.size}")
        } catch (e: Exception) {
            android.util.Log.e("AmazonTracking", "Error: ${e.message}")
        }
    }

    suspend fun archiveShipment(id: String) {
        dao.archiveShipment(id)
    }

    suspend fun deleteShipment(id: String) {
        dao.deleteShipment(id)
    }

    private fun parseAmazonDate(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        
        // 1. Intentar ISO 8601 (formato API)
        try {
            if (dateStr.contains("T") && dateStr.endsWith("Z")) {
                val cleanDate = if (dateStr.length > 19) dateStr.substring(0, 19) else dateStr.replace("Z", "")
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                return isoFormat.parse(cleanDate)?.time
            }
        } catch (e: Exception) {
            // Ignorar y seguir
        }

        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)

        // Pre-procesamiento para Español:
        // - Minúsculas: "Febrero" → "febrero" (SimpleDateFormat en Android para 'es' requiere minúsculas)
        // - Normalizar AM/PM: Amazon móvil muestra "5:14 PM" en inglés incluso en páginas en español.
        //   Locale("es","ES") no reconoce "PM" → normalizamos a "p.m." antes de parsear.
        val lowerDateStr = dateStr.lowercase()
            .replace(" pm", " p.m.")
            .replace(" am", " a.m.")

        val formats = listOf(
            // Formato Amazon mobile español: "miércoles, 18 de febrero 5:14 p.m."
            // (después de normalizar "PM" → "p.m." que es lo que Locale es_ES reconoce)
            Pair("EEEE, d 'de' MMMM h:mm a", Locale("es", "ES")),
            Pair("EEEE d 'de' MMMM h:mm a", Locale("es", "ES")),
            Pair("d 'de' MMMM h:mm a", Locale("es", "ES")),
            // Variante con dd (día con cero)
            Pair("EEEE, dd 'de' MMMM h:mm a", Locale("es", "ES")),
            Pair("EEEE dd 'de' MMMM h:mm a", Locale("es", "ES")),
            Pair("dd 'de' MMMM h:mm a", Locale("es", "ES")),
            // Español con hora 24h
            Pair("EEEE, d 'de' MMMM HH:mm", Locale("es", "ES")),
            Pair("EEEE d 'de' MMMM HH:mm", Locale("es", "ES")),
            Pair("EEEE, dd 'de' MMMM HH:mm", Locale("es", "ES")),
            Pair("EEEE dd 'de' MMMM HH:mm", Locale("es", "ES")),
            Pair("dd 'de' MMMM HH:mm", Locale("es", "ES")),
            // Español solo fecha
            Pair("EEEE, d 'de' MMMM", Locale("es", "ES")),
            Pair("EEEE d 'de' MMMM", Locale("es", "ES")),
            Pair("EEEE, dd 'de' MMMM", Locale("es", "ES")),
            Pair("EEEE dd 'de' MMMM", Locale("es", "ES")),
            Pair("d 'de' MMMM", Locale("es", "ES")),
            Pair("dd 'de' MMMM", Locale("es", "ES")),

            // Inglés - Usamos dateStr original
            Pair("EEEE, MMMM d h:mm a", Locale.US),
            Pair("MMMM d, h:mm a", Locale.US),
            Pair("EEEE, MMMM d", Locale.US),
            Pair("MMMM d", Locale.US),

            // Variaciones simples
            Pair("dd/MM/yyyy HH:mm", Locale.getDefault()),
            Pair("dd/MM/yyyy", Locale.getDefault())
        )
        
        for ((pattern, locale) in formats) {
            try {
                // Seleccionar string según idioma del patrón
                val input = if (locale.language == "es") lowerDateStr else dateStr
                
                val format = SimpleDateFormat(pattern, locale)
                format.isLenient = true // Permitir cierta flexibilidad
                
                val date = format.parse(input) ?: continue
                
                val calendar = Calendar.getInstance()
                calendar.time = date
                
                // Si el año es 1970 (default cuando no hay año), inferirlo
                if (calendar.get(Calendar.YEAR) == 1970) {
                    calendar.set(Calendar.YEAR, currentYear)
                    // Si la fecha resultante es futura (> 24h), asumimos que fue el año pasado
                    if (calendar.timeInMillis > now.timeInMillis + 86400000L) {
                        calendar.add(Calendar.YEAR, -1)
                    }
                }
                return calendar.timeInMillis
            } catch (e: Exception) {
                // Probar siguiente formato
            }
        }
        
        android.util.Log.w("ShipmentRepository", "No se pudo parsear fecha: $dateStr")
        return null
    }
}

