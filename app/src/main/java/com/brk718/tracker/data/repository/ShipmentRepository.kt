package com.brk718.tracker.data.repository

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.brk718.tracker.data.local.ShipmentDao
import com.brk718.tracker.data.local.ShipmentEntity
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.TrackingEventEntity
import com.brk718.tracker.data.remote.AmazonTrackingService
import com.brk718.tracker.data.remote.CarrierScraperFactory
import com.brk718.tracker.data.remote.ColombianCarrierScraper
import com.brk718.tracker.data.remote.GeocodingService
import com.brk718.tracker.data.remote.InterrapidisimoScraper
import com.brk718.tracker.data.remote.CreateTrackingBody
import com.brk718.tracker.data.remote.CreateTrackingRequest
import com.brk718.tracker.data.remote.DetectCourierBody
import com.brk718.tracker.data.remote.DetectCourierRequest
import com.brk718.tracker.data.remote.TrackingApi
import com.brk718.tracker.ui.widget.TrackerWidget
import com.brk718.tracker.util.StatusTranslator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShipmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ShipmentDao,
    private val api: TrackingApi,
    private val amazonService: AmazonTrackingService,
    private val interrapidisimoScraper: InterrapidisimoScraper,
    private val scraperFactory: CarrierScraperFactory,
    private val geocodingService: GeocodingService
) {
    companion object {
        // ── SimpleDateFormat cacheados (ThreadLocal = thread-safe con Dispatchers.IO) ───────────
        /** ISO 8601 con hora: "yyyy-MM-dd'T'HH:mm:ss" */
        private val FMT_ISO_DATETIME: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).also {
                it.timeZone = TimeZone.getTimeZone("UTC")
            }
        }
        /** ISO 8601 solo fecha: "yyyy-MM-dd" */
        private val FMT_ISO_DATE: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).also {
                it.timeZone = TimeZone.getTimeZone("UTC")
            }
        }
        /** Formatos de fecha usados por Interrapidísimo — lista inmutable, reutilizada entre llamadas */
        private val INTERRAPIDISIMO_DATE_FORMATS: List<Pair<String, Locale>> = listOf(
            Pair("dd/MM/yyyy HH:mm:ss", Locale.getDefault()),
            Pair("dd/MM/yyyy HH:mm",    Locale.getDefault()),
            Pair("dd/MM/yyyy",          Locale.getDefault()),
            Pair("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            Pair("yyyy-MM-dd HH:mm:ss", Locale.US),
            Pair("yyyy-MM-dd",          Locale.US)
        )

        /**
         * Carriers que van DIRECTO al scraper, sin pasar por AfterShip.
         * AfterShip no los soporta (devuelve 404 o no responde), por lo que
         * intentarlo solo causa freezes por timeout.
         */
        private val SCRAPER_ONLY_SLUGS = setOf(
            "servientrega",
            "coordinadora"   // AfterShip siempre devuelve 404 para coordinadora
        )

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
            // Internacionales (genéricos)
            "fedex" to "fedex",
            "ups" to "ups",
            "usps" to "usps",
            "dhl" to "dhl",
            "dhl express" to "dhl",
            "amazon" to "amazon",
            "amazon logistics" to "amazon",
            // ── México ──────────────────────────────────────────────────────────
            "estafeta" to "estafeta",
            "redpack" to "redpack",
            "paquetexpress" to "paquetexpress",
            "99minutos" to "99minutos",
            "j&t express mx" to "jet-express",
            "j&t express" to "jet-express",
            "jet-express" to "jet-express",
            "ivoy" to "ivoy",
            "correos de méxico" to "correos-de-mexico",
            "correos de mexico" to "correos-de-mexico",
            "correos-de-mexico" to "correos-de-mexico",
            // ── Chile ───────────────────────────────────────────────────────────
            "chilexpress" to "chilexpress",
            "starken" to "starken",
            "blue express" to "bluex-cl",
            "bluex" to "bluex-cl",
            "bluex-cl" to "bluex-cl",
            "correos de chile" to "correos-de-chile",
            "correos-de-chile" to "correos-de-chile",
            "shippify" to "shippify",
            // ── MercadoLibre (entrada manual; prefijo ME → auto-detect) ─────────
            "mercado envíos" to "mercadolibre",
            "mercado envios" to "mercadolibre",
            "mercadolibre" to "mercadolibre",
        )

        /**
         * Genera la URL de seguimiento en el sitio web del carrier para abrir en el navegador.
         * Devuelve null si el carrier no tiene URL de seguimiento conocida.
         */
        fun trackingUrl(carrier: String, trackingNumber: String): String? {
            val encoded = java.net.URLEncoder.encode(trackingNumber, "UTF-8")
            return when (carrier.lowercase().trim()) {
                "amazon"              -> "https://track.amazon.com/tracking/$trackingNumber"
                "fedex"               -> "https://www.fedex.com/apps/fedextrack/?tracknumbers=$encoded"
                "ups"                 -> "https://www.ups.com/track?loc=es&tracknum=$encoded"
                "usps"                -> "https://tools.usps.com/go/TrackConfirmAction?tLabels=$encoded"
                "dhl"                 -> "https://www.dhl.com/es-co-en/home/tracking.html?tracking-id=$encoded"
                "coordinadora"        -> "https://coordinadora.com/rastreo/rastreo-de-guia/detalle-de-rastreo-de-guia/?guia=$encoded"
                "servientrega"        -> "https://www.servientrega.com/wps/portal/rastreo-envio?tracking=$encoded"
                "interrapidisimo-scraper", "inter-rapidisimo"
                                      -> "https://www.interrapidisimo.com/rastreo/?tracking=$encoded"
                "envia-co"            -> "https://www.envia.co/rastreo?guia=$encoded"
                "tcc-co"              -> "https://www.tcc.com.co/rastreo/?codigo=$encoded"
                "deprisa"             -> "https://www.deprisa.com/rastrear?guia=$encoded"
                "saferbo"             -> "https://www.saferbo.com.co/rastreo?guia=$encoded"
                "472-co"              -> "https://www.472.com.co/rastreo?guia=$encoded"
                "logysto"             -> "https://www.logysto.com/rastreo?guia=$encoded"
                "listo"               -> "https://www.listo.com.co/rastreo?guia=$encoded"
                "treda"               -> "https://www.treda.co/rastreo?guia=$encoded"
                "speed-co"            -> "https://www.speed.com.co/rastreo?guia=$encoded"
                "castores"            -> "https://www.castores.com.co/rastreo?guia=$encoded"
                "avianca-cargo"       -> "https://www.aviancacargo.com/rastreo?guia=$encoded"
                "picap"               -> "https://www.picap.app/rastreo?guia=$encoded"
                "mensajerosurbanos"   -> "https://www.mensajerosurbanos.com/rastreo?guia=$encoded"
                "pasarex"             -> "https://pasarex.com/co/"
                // ── México ──────────────────────────────────────────────────────
                "estafeta"            -> "https://www.estafeta.com/herramientas/rastreo?guiaNumbers=$encoded"
                "redpack"             -> "https://www.redpack.com.mx/es/rastreo/?guia=$encoded"
                "paquetexpress"       -> "https://www.paquetexpress.com.mx/rastreo/$encoded"
                "99minutos"           -> "https://www.99minutos.com/track/$encoded"
                "jet-express"         -> "https://www.jtexpress.mx/index/query/gzquery.html?bills=$encoded"
                "ivoy"                -> "https://www.ivoy.com.mx/tracking/$encoded"
                "correos-de-mexico"   -> "https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/Rastreo.aspx?id=$encoded"
                // ── Chile ────────────────────────────────────────────────────────
                "chilexpress"         -> "https://www.chilexpress.cl/views/chilevision/informacion-de-envio.aspx?NroDocumento=$encoded"
                "starken"             -> "https://www.starken.cl/seguimiento?codigo=$encoded"
                "bluex-cl"            -> "https://www.bluex.cl/tracking/?tracking=$encoded"
                "correos-de-chile"    -> "https://www.correos.cl/seguimiento-de-envios/?n_envio=$encoded"
                "shippify"            -> "https://app.shippify.co/tracking/$encoded"
                // ── MercadoLibre (varios slugs posibles del auto-detect) ─────────
                "mercadolibre",
                "mercadolibre-mx",
                "mercadolibre-cl",
                "mercadolibre-co",
                "mercadolibre-ar",
                "mercadolibre-br"     -> "https://www.mercadolibre.com/envios/tracking?search_type=package&package_id=$encoded"
                else                  -> null
            }
        }

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
            // ── México ──────────────────────────────────────────────────────────
            "estafeta"            -> "Estafeta"
            "redpack"             -> "Redpack"
            "paquetexpress"       -> "Paquetexpress"
            "99minutos"           -> "99 Minutos"
            "jet-express"         -> "J&T Express MX"
            "ivoy"                -> "iVoy"
            "correos-de-mexico"   -> "Correos de México"
            // ── Chile ────────────────────────────────────────────────────────────
            "chilexpress"         -> "Chilexpress"
            "starken"             -> "Starken"
            "bluex-cl"            -> "Blue Express"
            "correos-de-chile"    -> "Correos de Chile"
            "shippify"            -> "Shippify"
            // ── MercadoLibre (varios slugs posibles del auto-detect) ─────────────
            "mercadolibre",
            "mercadolibre-mx",
            "mercadolibre-cl",
            "mercadolibre-co",
            "mercadolibre-ar",
            "mercadolibre-br"     -> "Mercado Envíos"
            "manual"              -> "Manual"
            else                  -> carrier.replaceFirstChar { it.uppercaseChar() }
        }
    }

    // Un Mutex por shipment ID — evita que SyncWorker y pull-to-refresh refresquen el mismo
    // envío simultáneamente (race condition de escritura en la DB)
    private val refreshLocks = ConcurrentHashMap<String, Mutex>()

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
                notifyWidget()
                return@withContext
            }

            // Paso 2b: Carriers con scraper directo (AfterShip no los soporta → freeze/timeout)
            if (slug != null && slug in SCRAPER_ONLY_SLUGS) {
                val directScraper = scraperFactory.getFor(slug)
                if (directScraper != null) {
                    android.util.Log.d("Tracking",
                        "Usando scraper directo para $slug/$trackingNumber (no pasa por AfterShip)")
                    dao.insertShipment(shipment.copy(carrier = slug, status = "Registrando..."))
                    refreshShipmentWithScraper(id, shipment.copy(carrier = slug), directScraper)
                    notifyWidget()
                    return@withContext
                }
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
                val carrierSlug = slug ?: carrier.ifBlank { "manual" }
                // AfterShip rechazó el número — intentar scraper directo si existe uno
                val directScraper = scraperFactory.getFor(carrierSlug)
                if (directScraper != null) {
                    android.util.Log.d("AfterShip",
                        "AfterShip rechazó $carrierSlug/$trackingNumber → intentando scraper")
                    dao.insertShipment(shipment.copy(carrier = carrierSlug, status = "Registrando..."))
                    try {
                        val scraperResult = directScraper.getTracking(trackingNumber)
                        if (scraperResult.error == null && scraperResult.status != null) {
                            dao.insertShipment(shipment.copy(
                                carrier = carrierSlug,
                                status = scraperResult.status,
                                lastUpdate = System.currentTimeMillis()
                            ))
                            if (scraperResult.events.isNotEmpty()) {
                                val scraperEvents = scraperResult.events.mapIndexed { index, event ->
                                    TrackingEventEntity(0L, id,
                                        System.currentTimeMillis() - (index * 3_600_000L),
                                        event.description, event.location, "")
                                }
                                dao.clearEventsForShipment(id)
                                dao.insertEvents(scraperEvents)
                            }
                        } else {
                            dao.insertShipment(shipment.copy(carrier = carrierSlug, status = "Seguimiento manual"))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AfterShip", "Scraper falló para $carrierSlug: ${e.message}")
                        dao.insertShipment(shipment.copy(carrier = carrierSlug, status = "Seguimiento manual"))
                    }
                } else {
                    dao.insertShipment(shipment.copy(
                        carrier = carrierSlug,
                        status = "Seguimiento manual"
                    ))
                }
            }
            notifyWidget()
        }
    }

    suspend fun refreshShipment(id: String) = withContext(Dispatchers.IO) {
        val lock = refreshLocks.getOrPut(id) { Mutex() }
        lock.withLock {
        val shipmentWithEvents = dao.getShipmentById(id).first() ?: return@withLock
        var shipment = shipmentWithEvents.shipment

        // Auto-corregir título genérico heredado de versiones anteriores
        if (shipment.title == "Envío sin título" || shipment.title.isBlank()) {
            shipment = shipment.copy(title = shipment.trackingNumber)
            dao.insertShipment(shipment)
        }

        // Si es un tracking de Amazon, usar el servicio de Amazon directamente
        if (amazonService.isAmazonTracking(shipment.trackingNumber)) {
            refreshShipmentAmazon(id)
            return@withLock
        }

        // Si es Interrapidísimo, usar el scraper directo
        if (shipment.carrier == "interrapidisimo-scraper" ||
            InterrapidisimoScraper.isInterrapidisimoTracking(shipment.trackingNumber)) {
            refreshShipmentInterrapidisimo(id)
            return@withLock
        }

        // Resolver el slug correcto: el carrier guardado puede ser el nombre (ej. "interrapidisimo")
        // o ya el slug (ej. "inter-rapidisimo"). Intentar resolución siempre.
        val effectiveSlug = resolveSlug(shipment.carrier) ?: shipment.carrier

        // Si es un carrier que usa scraper directo, saltar AfterShip completamente
        if (effectiveSlug in SCRAPER_ONLY_SLUGS) {
            scraperFactory.getFor(effectiveSlug)?.let { scraper ->
                android.util.Log.d("ShipmentRepository",
                    "Scraper directo para $effectiveSlug/${shipment.trackingNumber} (sin AfterShip)")
                refreshShipmentWithScraper(id, shipment, scraper)
            }
            return@withLock
        }
        // Si el slug efectivo difiere del carrier guardado, corregirlo en la BD
        if (effectiveSlug != shipment.carrier) {
            dao.insertShipment(shipment.copy(carrier = effectiveSlug))
            android.util.Log.d("AfterShip", "Corrigiendo carrier guardado: ${shipment.carrier} → $effectiveSlug")
        }

        try {
            // GET al API de AfterShip.
            // Si devuelve 404 el tracking no existe todavía → crearlo y reintentar una vez.
            val response = try {
                api.getTrackingInfo(effectiveSlug, shipment.trackingNumber)
            } catch (e404: retrofit2.HttpException) {
                if (e404.code() == 404) {
                    android.util.Log.w("ShipmentRepository",
                        "AfterShip 404 — auto-creando tracking $effectiveSlug/${shipment.trackingNumber}")
                    try {
                        api.createTracking(
                            CreateTrackingRequest(
                                tracking = CreateTrackingBody(
                                    tracking_number = shipment.trackingNumber,
                                    slug = effectiveSlug
                                )
                            )
                        )
                        android.util.Log.d("ShipmentRepository", "Auto-creado OK, reintentando GET…")
                    } catch (createEx: retrofit2.HttpException) {
                        // 409 / 4009 = el tracking ya existe en AfterShip → ok, continuar con el GET
                        if (createEx.code() != 409 && createEx.code() != 4009) {
                            android.util.Log.w("ShipmentRepository",
                                "Auto-create falló HTTP ${createEx.code()}: ${createEx.message()}")
                            // AfterShip no acepta este número → intentar scraper directo
                            scraperFactory.getFor(effectiveSlug)?.let {
                                android.util.Log.d("ShipmentRepository",
                                    "AfterShip no acepta $effectiveSlug/${shipment.trackingNumber}, usando scraper")
                                refreshShipmentWithScraper(id, shipment, it)
                            }
                            return@withLock
                        }
                        android.util.Log.d("ShipmentRepository",
                            "Tracking ya existía en AfterShip (${createEx.code()}), reintentando GET…")
                    }
                    // Reintento GET tras crear (o confirmar que ya existía)
                    api.getTrackingInfo(effectiveSlug, shipment.trackingNumber)
                } else {
                    throw e404   // Re-lanzar otros errores HTTP al catch externo
                }
            }
            val tracking = response.data?.tracking
            if (tracking == null) {
                // AfterShip no tiene datos — intentar scraper directo como fallback
                scraperFactory.getFor(effectiveSlug)?.let {
                    android.util.Log.d("ShipmentRepository",
                        "AfterShip sin datos para $effectiveSlug/${shipment.trackingNumber}, usando scraper")
                    refreshShipmentWithScraper(id, shipment, it)
                }
                return@withLock
            }

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

            // Si estimatedDelivery cambia, resetear reminderSent para re-enviar el recordatorio
            val newEstimatedDelivery = estimatedDeliveryMs ?: shipment.estimatedDelivery
            val resetReminder = estimatedDeliveryMs != null && estimatedDeliveryMs != shipment.estimatedDelivery

            dao.insertShipment(shipment.copy(
                status = statusText,
                lastUpdate = System.currentTimeMillis(),
                estimatedDelivery = newEstimatedDelivery,
                reminderSent = if (resetReminder) false else shipment.reminderSent
            ))

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
            // Intentar scraper directo como fallback si AfterShip lanzó excepción
            scraperFactory.getFor(effectiveSlug)?.let {
                android.util.Log.d("ShipmentRepository",
                    "AfterShip falló para $effectiveSlug/${shipment.trackingNumber}, usando scraper")
                refreshShipmentWithScraper(id, shipment, it)
            }
        }
        } // lock.withLock
    }

    /**
     * Fallback: obtiene datos de rastreo directamente del sitio del carrier cuando
     * AfterShip no retorna datos o falla.
     *
     * Solo se activa para carriers que tienen un [ColombianCarrierScraper] registrado
     * en [CarrierScraperFactory] (Servientrega, Coordinadora, TCC, Deprisa).
     */
    private suspend fun refreshShipmentWithScraper(
        id: String,
        shipment: ShipmentEntity,
        scraper: ColombianCarrierScraper
    ) = withContext(Dispatchers.IO) {
        try {
            val result = scraper.getTracking(shipment.trackingNumber)

            if (result.error != null || result.status == null) {
                android.util.Log.w("ShipmentRepository",
                    "Scraper sin datos para ${shipment.trackingNumber}: ${result.error}")
                // Si el envío está en "Registrando..." (recién agregado), cambiarlo a un estado
                // terminal para evitar que quede congelado para siempre.
                if (shipment.status == "Registrando...") {
                    dao.insertShipment(shipment.copy(
                        status = "Seguimiento manual",
                        lastUpdate = System.currentTimeMillis()
                    ))
                }
                return@withContext
            }

            dao.insertShipment(shipment.copy(
                status = result.status,
                lastUpdate = System.currentTimeMillis()
            ))

            if (result.events.isNotEmpty()) {
                val geocodeCache = mutableMapOf<String, Pair<Double?, Double?>>()
                val events = result.events.mapIndexed { index, event ->
                    var lat: Double? = null
                    var lon: Double? = null
                    if (event.location.isNotBlank()) {
                        val cacheKey = event.location.trim().lowercase()
                        if (geocodeCache.containsKey(cacheKey)) {
                            val cached = geocodeCache[cacheKey]!!
                            lat = cached.first; lon = cached.second
                        } else {
                            val coords = geocodingService.getCoordinates("${event.location}, Colombia")
                            lat = coords?.lat; lon = coords?.lon
                            geocodeCache[cacheKey] = Pair(lat, lon)
                        }
                    }
                    TrackingEventEntity(
                        id = 0L,
                        shipmentId = id,
                        timestamp = parseInterrapidisimoDate(event.timestamp)
                            ?: (System.currentTimeMillis() - (index * 3_600_000L)),
                        description = event.description,
                        location = event.location,
                        status = "",
                        latitude = lat,
                        longitude = lon
                    )
                }
                dao.clearEventsForShipment(id)
                dao.insertEvents(events)
            }

            android.util.Log.d("ShipmentRepository",
                "Scraper OK: ${shipment.trackingNumber} → ${result.status}, ${result.events.size} eventos")

        } catch (e: Exception) {
            android.util.Log.e("ShipmentRepository",
                "Scraper falló para ${shipment.trackingNumber}: ${e.message}", e)
            // Si el envío sigue en "Registrando...", sacarlo de ese estado para evitar
            // que quede congelado cuando OkHttp lanza SocketTimeoutException u otra excepción.
            if (shipment.status == "Registrando...") {
                dao.insertShipment(shipment.copy(
                    status = "Seguimiento manual",
                    lastUpdate = System.currentTimeMillis()
                ))
            }
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
                lastUpdate = System.currentTimeMillis(),
                subCarrierName = result.subCarrierName ?: shipment.subCarrierName,
                subCarrierTrackingId = result.subCarrierTrackingId ?: shipment.subCarrierTrackingId
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
        // Reutilizar la lista de formatos cacheada — solo se crea SimpleDateFormat por iteración
        for ((pattern, locale) in INTERRAPIDISIMO_DATE_FORMATS) {
            try {
                return SimpleDateFormat(pattern, locale).parse(dateStr.trim())?.time
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
        notifyWidget()
    }

    suspend fun unarchiveShipment(id: String) {
        dao.unarchiveShipment(id)
        notifyWidget()
    }

    suspend fun deleteShipment(id: String) {
        dao.deleteShipment(id)
        notifyWidget()
    }

    suspend fun muteShipment(id: String)     = dao.updateMuted(id, true)
    suspend fun unmuteShipment(id: String)   = dao.updateMuted(id, false)
    suspend fun markReminderSent(id: String) = dao.updateReminderSent(id, true)

    /** Dispara una actualización inmediata del widget de pantalla de inicio. */
    private suspend fun notifyWidget() {
        try {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TrackerWidget::class.java)
            glanceIds.forEach { glanceId ->
                TrackerWidget().update(context, glanceId)
            }
        } catch (e: Exception) {
            android.util.Log.w("ShipmentRepository", "Error actualizando widget: ${e.message}")
        }
    }

    suspend fun countAllShipments(): Int = withContext(Dispatchers.IO) { dao.countAllShipments() }
    suspend fun countDeliveredShipments(): Int = withContext(Dispatchers.IO) { dao.countDeliveredShipments() }

    /** Parsea un string ISO 8601 (ej. "2025-02-28T00:00:00") a timestamp en milisegundos. */
    private fun parseIso8601Date(dateStr: String): Long? {
        return try {
            FMT_ISO_DATETIME.get()!!.parse(dateStr.take(19))?.time
        } catch (e: Exception) {
            try {
                FMT_ISO_DATE.get()!!.parse(dateStr.take(10))?.time
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
                return FMT_ISO_DATETIME.get()!!.parse(cleanDate)?.time
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

