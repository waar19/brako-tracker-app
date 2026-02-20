package com.brk718.tracker.data.repository

import com.brk718.tracker.data.local.ShipmentDao
import com.brk718.tracker.data.local.ShipmentEntity
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.TrackingEventEntity
import com.brk718.tracker.data.remote.AmazonTrackingService
import com.brk718.tracker.data.remote.InterrapidisimoScraper
import com.brk718.tracker.data.remote.CreateTrackingBody
import com.brk718.tracker.data.remote.CreateTrackingRequest
import com.brk718.tracker.data.remote.DetectCourierBody
import com.brk718.tracker.data.remote.DetectCourierRequest
import com.brk718.tracker.data.remote.TrackingApi
import com.brk718.tracker.util.StatusTranslator
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
    private val amazonService: AmazonTrackingService,
    private val interrapidisimoScraper: InterrapidisimoScraper
) {
    companion object {
        // Mapeo conocido: nombre del carrier → slug de AfterShip
        // AfterShip se encarga del scraping (incluyendo CAPTCHAs)
        private val CARRIER_SLUG_MAP = mapOf(
            // Carriers colombianos
            "coordinadora" to "coordinadora",
            "servientrega" to "servientrega",
            // Interrapidísimo → scraper directo (AfterShip no lo soporta)
            "inter rapidísimo" to "interrapidisimo-scraper",
            "inter rapidisimo" to "interrapidisimo-scraper",
            "interrapidísimo" to "interrapidisimo-scraper",   // con tilde (AddScreen)
            "interrapidisimo" to "interrapidisimo-scraper",
            "inter-rapidisimo" to "interrapidisimo-scraper",
            "interrapidisimo-scraper" to "interrapidisimo-scraper",
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
            // Carriers colombianos adicionales
            "listo" to "listo",
            "treda" to "treda",
            "speed" to "speed-co",
            "speed-co" to "speed-co",
            "castores" to "castores",
            "avianca cargo" to "avianca-cargo",
            "avianca-cargo" to "avianca-cargo",
            "la 14" to "la-14",
            "la14" to "la-14",
            "picap" to "picap",
            "mensajeros urbanos" to "mensajerosurbanos",
            "mensajerosurbanos" to "mensajerosurbanos",
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
         * Resuelve el slug de AfterShip (o slug interno) a partir del nombre del carrier
         */
        fun resolveSlug(carrier: String): String? {
            val normalized = carrier.lowercase().trim()
            return CARRIER_SLUG_MAP[normalized]
        }

        /**
         * Convierte un slug interno o slug de AfterShip a un nombre para mostrar al usuario.
         */
        fun displayName(carrier: String): String = when (carrier.lowercase().trim()) {
            "interrapidisimo-scraper", "inter-rapidisimo" -> "Interrapidísimo"
            "coordinadora"        -> "Coordinadora"
            "servientrega"        -> "Servientrega"
            "envia-co"            -> "Envía"
            "tcc-co"              -> "TCC"
            "472-co"              -> "472"
            "logysto"             -> "Logysto"
            "saferbo"             -> "Saferbo"
            "deprisa"             -> "Deprisa"
            // Carriers adicionales colombianos
            "listo"               -> "Listo"
            "treda"               -> "Treda"
            "speed-co"            -> "Speed"
            "castores"            -> "Castores"
            "avianca-cargo"       -> "Avianca Cargo"
            "la-14"               -> "La 14"
            "picap"               -> "Picap"
            "mensajerosurbanos"   -> "Mensajeros Urbanos"
            // Internacionales
            "amazon"              -> "Amazon"
            "fedex"               -> "FedEx"
            "ups"                 -> "UPS"
            "usps"                -> "USPS"
            "dhl"                 -> "DHL"
            "manual"              -> "Manual"
            else                  -> carrier.replaceFirstChar { it.uppercaseChar() }
        }
    }

    val activeShipments: Flow<List<ShipmentWithEvents>> = dao.getAllActiveShipments()
    val archivedShipments: Flow<List<ShipmentWithEvents>> = dao.getAllArchivedShipments()
    /** Todos los envíos (activos + archivados) — usado por StatsViewModel. */
    val allShipments: Flow<List<ShipmentWithEvents>> = dao.getAllShipmentsWithEvents()

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

            // Paso 2: Si el slug es interrapidisimo-scraper, usar scraper directo
            if (slug == "interrapidisimo-scraper" ||
                InterrapidisimoScraper.isInterrapidisimoTracking(trackingNumber)) {
                android.util.Log.d("Tracking", "Usando Interrapidísimo scraper para $trackingNumber")
                dao.insertShipment(shipment.copy(carrier = "interrapidisimo-scraper"))
                refreshShipmentInterrapidisimo(id)
                return@withContext
            }

            // Paso 2: Si tenemos slug, crear tracking en AfterShip
            var afterShipOk = false
            if (slug != null && slug != "amazon") {
                // Guardar el slug correcto en BD antes de consultar (corrige carrier guardado)
                dao.insertShipment(shipment.copy(carrier = slug))
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
                    android.util.Log.d("AfterShip", "Tracking creado: $slug/$trackingNumber")
                } catch (e: retrofit2.HttpException) {
                    // 4009 = tracking ya existe → es válido, podemos consultar igual
                    if (e.code() == 4009 || e.code() == 409) {
                        afterShipOk = true
                        android.util.Log.d("AfterShip", "Tracking ya existe en AfterShip, consultando: $slug/$trackingNumber")
                    } else {
                        android.util.Log.w("AfterShip", "Create tracking falló HTTP ${e.code()}: ${e.message()}")
                    }
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
        var shipment = shipmentWithEvents.shipment

        // Auto-corregir título genérico heredado de versiones anteriores
        if (shipment.title == "Envío sin título" || shipment.title.isBlank()) {
            shipment = shipment.copy(title = shipment.trackingNumber)
            dao.insertShipment(shipment)
        }

        // Si es un tracking de Amazon, usar el servicio de Amazon directamente
        if (amazonService.isAmazonTracking(shipment.trackingNumber)) {
            refreshShipmentAmazon(id)
            return@withContext
        }

        // Si es Interrapidísimo, usar el scraper directo
        if (shipment.carrier == "interrapidisimo-scraper" ||
            InterrapidisimoScraper.isInterrapidisimoTracking(shipment.trackingNumber)) {
            refreshShipmentInterrapidisimo(id)
            return@withContext
        }

        // Resolver el slug correcto: el carrier guardado puede ser el nombre (ej. "interrapidisimo")
        // o ya el slug (ej. "inter-rapidisimo"). Intentar resolución siempre.
        val effectiveSlug = resolveSlug(shipment.carrier) ?: shipment.carrier
        // Si el slug efectivo difiere del carrier guardado, corregirlo en la BD
        if (effectiveSlug != shipment.carrier) {
            dao.insertShipment(shipment.copy(carrier = effectiveSlug))
            android.util.Log.d("AfterShip", "Corrigiendo carrier guardado: ${shipment.carrier} → $effectiveSlug")
        }

        try {
            val response = api.getTrackingInfo(effectiveSlug, shipment.trackingNumber)
            val tracking = response.data?.tracking ?: return@withContext

            // Traducir el tag principal al español; si el subtag_message aporta más detalle,
            // intentar traducirlo también y usar el más descriptivo (el subtag es más específico).
            val tagTranslation = StatusTranslator.translateTag(tracking.tag)
            val subtagTranslation = tracking.subtag_message
                ?.let { StatusTranslator.translateMessage(it) }
            // Preferir subtag traducido si difiere del raw (significa que aportó info real)
            val statusText = if (subtagTranslation != null && subtagTranslation != tracking.subtag_message) {
                subtagTranslation
            } else {
                tagTranslation
            }

            // Parsear fecha estimada de entrega si AfterShip la devuelve
            val estimatedDeliveryMs = tracking.expected_delivery
                ?.takeIf { it.isNotBlank() }
                ?.let { parseIso8601Date(it) }

            dao.insertShipment(shipment.copy(
                status = statusText,
                lastUpdate = System.currentTimeMillis(),
                estimatedDelivery = estimatedDeliveryMs ?: shipment.estimatedDelivery
            ))

            // Auto-archivar si el envío fue entregado
            if (statusText == "Entregado") {
                dao.archiveShipment(id)
                android.util.Log.d("Tracking", "Auto-archivado envío entregado: $id")
            }

            val events = tracking.checkpoints.mapIndexed { index, checkpoint ->
                val rawDescription = checkpoint.message
                    ?: checkpoint.subtag_message
                    ?: "Sin descripción"
                TrackingEventEntity(
                    id = 0L,
                    shipmentId = id,
                    timestamp = System.currentTimeMillis() - (index * 3600000L),
                    description = StatusTranslator.translateMessage(rawDescription),
                    location = checkpoint.location ?: "",
                    status = checkpoint.tag?.let { StatusTranslator.translateTag(it) } ?: ""
                )
            }
            dao.clearEventsForShipment(id)
            dao.insertEvents(events)

        } catch (e: Exception) {
            android.util.Log.e("ShipmentRepository", "Error al refrescar envío AfterShip: ${e.message}", e)
        }
    }

    private suspend fun refreshShipmentAmazon(id: String) = withContext(Dispatchers.IO) {
        val shipmentWithEvents = dao.getShipmentById(id).first() ?: return@withContext
        val shipment = shipmentWithEvents.shipment
        // Auto-corregir título genérico heredado de versiones anteriores
        val fixedTitle = if (shipment.title == "Envío sin título" || shipment.title.isBlank())
            shipment.trackingNumber else shipment.title

        try {
            val result = amazonService.getTracking(shipment.trackingNumber)

            if (result.error == "LOGIN_REQUIRED") {
                android.util.Log.w("AmazonTracking", "Requiere login")
                dao.insertShipment(shipment.copy(
                    carrier = "Amazon",
                    title = fixedTitle,
                    status = "LOGIN_REQUIRED",
                    lastUpdate = System.currentTimeMillis()
                ))
                return@withContext
            }

            if (result.error != null) {
                android.util.Log.w("AmazonTracking", "Error: ${result.error}")
                dao.insertShipment(shipment.copy(
                    carrier = "Amazon",
                    title = fixedTitle,
                    status = "Seguimiento manual",
                    lastUpdate = System.currentTimeMillis()
                ))
                return@withContext
            }

            // Actualizar estado
            val status = result.status ?: "En seguimiento"
            dao.insertShipment(shipment.copy(
                carrier = "Amazon",
                title = fixedTitle,
                status = status,
                lastUpdate = System.currentTimeMillis()
            ))

            // Auto-archivar si el envío fue entregado
            if (status.lowercase().let { it.contains("entregado") || it.contains("delivered") }) {
                dao.archiveShipment(id)
                android.util.Log.d("Tracking", "Auto-archivado envío Amazon entregado: $id")
            }

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

    private suspend fun refreshShipmentInterrapidisimo(id: String) = withContext(Dispatchers.IO) {
        val shipmentWithEvents = dao.getShipmentById(id).first() ?: return@withContext
        val shipment = shipmentWithEvents.shipment

        try {
            val result = interrapidisimoScraper.getTracking(shipment.trackingNumber)

            if (result.error != null) {
                android.util.Log.w("InterrapidisimoTracking", "Error: ${result.error}")
                dao.insertShipment(shipment.copy(
                    carrier = "interrapidisimo-scraper",
                    status = "Seguimiento manual",
                    lastUpdate = System.currentTimeMillis()
                ))
                return@withContext
            }

            val displayStatus = result.status ?: "En seguimiento"
            // Auto-corregir título genérico heredado de versiones anteriores
            val fixedTitle = if (shipment.title == "Envío sin título" || shipment.title.isBlank())
                shipment.trackingNumber else shipment.title

            dao.insertShipment(shipment.copy(
                carrier = "interrapidisimo-scraper",
                status = displayStatus,
                title = fixedTitle,
                lastUpdate = System.currentTimeMillis()
            ))

            // Auto-archivar si el envío fue entregado
            if (displayStatus.lowercase().contains("entregado")) {
                dao.archiveShipment(id)
                android.util.Log.d("Tracking", "Auto-archivado envío Interrapidísimo entregado: $id")
            }

            // Guardar eventos
            if (result.events.isNotEmpty()) {
                val events = result.events.mapIndexed { index, event ->
                    val realTimestamp = parseInterrapidisimoDate(event.timestamp)
                    val finalTimestamp = realTimestamp
                        ?: (System.currentTimeMillis() - (index * 3600000L))

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

            android.util.Log.d("InterrapidisimoTracking",
                "Estado: ${result.status}, Eventos: ${result.events.size}")
        } catch (e: Exception) {
            android.util.Log.e("InterrapidisimoTracking", "Error: ${e.message}")
        }
    }

    private fun parseInterrapidisimoDate(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        // Formatos posibles: "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy", "yyyy-MM-dd HH:mm:ss"
        val formats = listOf(
            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()),
            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
            java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        for (fmt in formats) {
            try {
                return fmt.parse(dateStr.trim())?.time
            } catch (_: Exception) {}
        }
        android.util.Log.w("ShipmentRepository", "No se pudo parsear fecha Inter: $dateStr")
        return null
    }

    suspend fun updateTitle(id: String, newTitle: String) {
        dao.updateTitle(id, newTitle)
    }

    suspend fun archiveShipment(id: String) {
        dao.archiveShipment(id)
    }

    suspend fun unarchiveShipment(id: String) {
        dao.unarchiveShipment(id)
    }

    suspend fun deleteShipment(id: String) {
        dao.deleteShipment(id)
    }

    suspend fun countAllShipments(): Int = withContext(Dispatchers.IO) { dao.countAllShipments() }
    suspend fun countDeliveredShipments(): Int = withContext(Dispatchers.IO) { dao.countDeliveredShipments() }

    /** Parsea un string ISO 8601 (ej. "2025-02-28T00:00:00") a timestamp en milisegundos. */
    private fun parseIso8601Date(dateStr: String): Long? {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(dateStr.take(19))?.time
        } catch (e: Exception) {
            try {
                val fmtDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                fmtDate.timeZone = TimeZone.getTimeZone("UTC")
                fmtDate.parse(dateStr.take(10))?.time
            } catch (e2: Exception) {
                null
            }
        }
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

