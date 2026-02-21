package com.brk718.tracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── Data models ──────────────────────────────────────────────────────────────

data class MonthBar(
    val label: String,   // "Ene", "Feb", etc.
    val count: Int
)

data class StatusSlice(
    val label: String,
    val count: Int,
    val colorHex: Long   // 0xFFRRGGBB
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val totalShipments: Int = 0,
    val deliveredShipments: Int = 0,
    val activeShipments: Int = 0,
    val successRate: Int = 0,           // porcentaje 0-100
    val avgDeliveryDays: Float = 0f,    // promedio de días de entrega
    val topCarrier: String = "",        // transportista más usado
    val topCarrierCount: Int = 0,
    val staleShipments: Int = 0,        // activos sin movimiento en >3 días
    val monthBars: List<MonthBar> = emptyList(),   // últimos 6 meses
    val statusSlices: List<StatusSlice> = emptyList()  // dona de estado
)

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val shipmentRepository: ShipmentRepository
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = shipmentRepository.allShipments
        .map { list -> buildStats(list) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState()
        )

    private fun buildStats(shipments: List<ShipmentWithEvents>): StatsUiState {
        if (shipments.isEmpty()) return StatsUiState(isLoading = false)

        val total     = shipments.size
        val delivered = shipments.count { it.shipment.status.equals("Entregado", ignoreCase = true) }
        val active    = shipments.count { !it.shipment.isArchived }
        val rate      = if (total > 0) (delivered * 100 / total) else 0

        // ── Transportista más usado ──────────────────────────────────────
        val carrierGroup = shipments.groupBy { it.shipment.carrier.lowercase().trim() }
        val topEntry        = carrierGroup.maxByOrNull { it.value.size }
        val topCarrier      = topEntry?.value?.firstOrNull()?.shipment?.carrier ?: ""
        val topCarrierCount = topEntry?.value?.size ?: 0

        // ── Tiempo promedio de entrega ───────────────────────────────────
        // Usa el primer evento disponible (o lastUpdate como fallback) hasta el último evento,
        // así incluye envíos con un solo checkpoint.
        val deliveryTimes = shipments
            .filter { it.shipment.status.equals("Entregado", ignoreCase = true) }
            .mapNotNull { swe ->
                val events = swe.events
                val lastTs = events.maxByOrNull { it.timestamp }?.timestamp
                    ?: return@mapNotNull null
                val firstTs = events.minByOrNull { it.timestamp }?.timestamp
                    ?: swe.shipment.lastUpdate  // fallback: usar lastUpdate si solo hay 1 evento
                val diffMs = lastTs - firstTs
                if (diffMs <= 0) null else diffMs / (1000L * 60 * 60 * 24).toFloat()
            }
        val avgDays = if (deliveryTimes.isEmpty()) 0f
                      else deliveryTimes.average().toFloat()

        // ── Sin movimiento: activos sin actualización en >3 días ─────────
        val staleCutoff = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)
        val stale = shipments.count { swe ->
            !swe.shipment.isArchived &&
            !swe.shipment.status.equals("Entregado", ignoreCase = true) &&
            swe.shipment.lastUpdate < staleCutoff
        }

        // ── Barras de los últimos 6 meses ────────────────────────────────
        val monthBars = buildMonthBars(shipments)

        // ── Dona de estado ────────────────────────────────────────────────
        val statusSlices = buildStatusSlices(shipments)

        return StatsUiState(
            isLoading          = false,
            totalShipments     = total,
            deliveredShipments = delivered,
            activeShipments    = active,
            successRate        = rate,
            avgDeliveryDays    = avgDays,
            topCarrier         = topCarrier,
            topCarrierCount    = topCarrierCount,
            staleShipments     = stale,
            monthBars          = monthBars,
            statusSlices       = statusSlices
        )
    }

    private fun buildMonthBars(shipments: List<ShipmentWithEvents>): List<MonthBar> {
        val cal = Calendar.getInstance()
        val monthFmt = SimpleDateFormat("MMM", Locale("es"))

        // Generar los últimos 6 meses (más reciente último = barra más a la derecha)
        val months = (5 downTo 0).map { offset ->
            cal.time = Date()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.MONTH, -offset)
            val year  = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val label = monthFmt.format(cal.time)
                .replaceFirstChar { it.uppercase() }
                .removeSuffix(".")
            Triple(year, month, label)
        }

        // Usamos el primer evento del envío (cuando fue creado/añadido) para la barra
        // del mes en que se rastreó, no la última actualización.
        val barCal = Calendar.getInstance()
        val bars = months.map { (year, month, label) ->
            val count = shipments.count { swe ->
                // Fecha de referencia: primer evento disponible o lastUpdate como fallback
                val refTs = swe.events.minByOrNull { it.timestamp }?.timestamp
                    ?: swe.shipment.lastUpdate
                barCal.time = Date(refTs)
                barCal.get(Calendar.YEAR) == year && barCal.get(Calendar.MONTH) == month
            }
            MonthBar(label = label, count = count)
        }
        return bars
    }

    private fun buildStatusSlices(shipments: List<ShipmentWithEvents>): List<StatusSlice> {
        val active = shipments.filter { !it.shipment.isArchived }
        if (active.isEmpty()) return emptyList()

        val entregado = active.count { it.shipment.status.equals("Entregado", ignoreCase = true) }
        val enTransito = active.count {
            val s = it.shipment.status.lowercase()
            s.contains("tránsito") || s.contains("transito") ||
            s.contains("reparto") || s.contains("camino")
        }
        val novedad = active.count {
            val s = it.shipment.status.lowercase()
            s.contains("novedad") || s.contains("problema") ||
            s.contains("inciden") || s.contains("devuelto") ||
            s.contains("dañado") || s.contains("perdido")
        }
        val pendiente = active.count {
            val s = it.shipment.status.lowercase()
            s.contains("pendiente") || s.contains("información") ||
            s.contains("recibida") || s.contains("etiqueta")
        }
        // "Otros" = lo que no encaja en ninguna categoría anterior
        val otros = active.size - entregado - enTransito - novedad - pendiente

        return buildList {
            if (entregado > 0)  add(StatusSlice("Entregado",   entregado,  0xFF22C55E))
            if (enTransito > 0) add(StatusSlice("En tránsito", enTransito, 0xFF3B82F6))
            if (pendiente > 0)  add(StatusSlice("Pendiente",   pendiente,  0xFFFFB400))
            if (novedad > 0)    add(StatusSlice("Novedad",     novedad,    0xFFEF4444))
            if (otros > 0)      add(StatusSlice("Otros",       otros,      0xFF94A3B8))
        }
    }
}
