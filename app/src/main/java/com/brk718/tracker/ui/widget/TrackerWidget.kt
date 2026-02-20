package com.brk718.tracker.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.brk718.tracker.MainActivity
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -- Claves del estado del widget ---------------------------------------------
private val KEY_IS_PREMIUM       = booleanPreferencesKey("widget_is_premium")
private val KEY_HAS_SHIPMENTS    = booleanPreferencesKey("widget_has_shipments")
private val KEY_SHIPMENT_TITLE   = stringPreferencesKey("widget_title")
private val KEY_SHIPMENT_STATUS  = stringPreferencesKey("widget_status")
private val KEY_SHIPMENT_CARRIER = stringPreferencesKey("widget_carrier")
private val KEY_LAST_UPDATED     = stringPreferencesKey("widget_last_updated")
private val KEY_SHIPMENT_COUNT   = stringPreferencesKey("widget_count")

// -- Entry point Hilt ---------------------------------------------------------
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun shipmentRepository(): ShipmentRepository
    fun userPreferencesRepository(): UserPreferencesRepository
}

// -- Widget principal ---------------------------------------------------------
class TrackerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        var isPremium    = false
        var hasShipments = false
        var title        = ""
        var status       = ""
        var carrier      = ""
        var dateStr      = ""
        var countStr     = ""

        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
            val repo      = entryPoint.shipmentRepository()
            val prefsRepo = entryPoint.userPreferencesRepository()

            val prefs     = prefsRepo.preferences.first()
            isPremium     = prefs.isPremium
            val shipments = repo.activeShipments.first()
            val latest    = shipments.firstOrNull()
            hasShipments  = shipments.isNotEmpty()
            countStr      = if (shipments.size > 1) "+${shipments.size - 1} mas" else ""

            dateStr = latest?.let {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.shipment.lastUpdate))
            } ?: ""

            title   = latest?.shipment?.title ?: ""
            status  = latest?.shipment?.status ?: ""
            carrier = latest?.let {
                ShipmentRepository.displayName(it.shipment.carrier)
            } ?: ""
        } catch (e: Exception) {
            android.util.Log.e("TrackerWidget", "Error cargando datos: ${e.message}", e)
        }

        updateAppWidgetState(context, id) { p ->
            p[KEY_IS_PREMIUM]       = isPremium
            p[KEY_HAS_SHIPMENTS]    = hasShipments
            p[KEY_SHIPMENT_TITLE]   = title
            p[KEY_SHIPMENT_STATUS]  = status
            p[KEY_SHIPMENT_CARRIER] = carrier
            p[KEY_LAST_UPDATED]     = dateStr
            p[KEY_SHIPMENT_COUNT]   = countStr
        }

        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

// -- Composicion del widget ---------------------------------------------------
@Composable
private fun WidgetContent() {
    val state        = currentState<Preferences>()
    val isPremium    = state[KEY_IS_PREMIUM] ?: false
    val hasShipments = state[KEY_HAS_SHIPMENTS] ?: false
    val title        = state[KEY_SHIPMENT_TITLE] ?: ""
    val status       = state[KEY_SHIPMENT_STATUS] ?: ""
    val carrier      = state[KEY_SHIPMENT_CARRIER] ?: ""
    val lastUpdated  = state[KEY_LAST_UPDATED] ?: ""
    val extraCount   = state[KEY_SHIPMENT_COUNT] ?: ""

    if (!hasShipments) {
        WidgetNoShipments()
    } else {
        WidgetShipmentInfo(
            title = title,
            status = status,
            carrier = carrier,
            lastUpdated = lastUpdated,
            isPremium = isPremium,
            extraCount = extraCount
        )
    }
}

// -- Estado: Sin envios -------------------------------------------------------
@Composable
private fun WidgetNoShipments() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barra de acento superior
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(4.dp)
                .background(GlanceTheme.colors.primary)
        ) {}

        Spacer(GlanceModifier.height(16.dp))

        Text(
            text = "Brako Tracker",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GlanceTheme.colors.onSurface
            )
        )

        Spacer(GlanceModifier.height(6.dp))

        Text(
            text = "Sin envios activos",
            style = TextStyle(
                fontSize = 12.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )

        Spacer(GlanceModifier.height(14.dp))

        Row(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primary)
                .cornerRadius(24.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Agregar envio",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onPrimary
                )
            )
        }

        Spacer(GlanceModifier.height(12.dp))
    }
}

// -- Estado: Con envio activo -------------------------------------------------
@Composable
private fun WidgetShipmentInfo(
    title: String,
    status: String,
    carrier: String,
    lastUpdated: String,
    isPremium: Boolean,
    extraCount: String
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(20.dp)
    ) {
        // ── Cabecera con fondo primaryContainer ───────────────────────────────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.primaryContainer)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Brako Tracker",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = GlanceTheme.colors.onPrimaryContainer
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            if (isPremium) {
                Row(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Premium",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = GlanceTheme.colors.onPrimary
                        )
                    )
                }
            }
        }

        // ── Contenido ─────────────────────────────────────────────────────────
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Transportista en pequeño encima del titulo
            if (carrier.isNotEmpty()) {
                Text(
                    text = carrier.uppercase(),
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = GlanceTheme.colors.primary
                    )
                )
                Spacer(GlanceModifier.height(2.dp))
            }

            // Titulo
            Text(
                text = title,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )

            Spacer(GlanceModifier.height(8.dp))

            // Chip de estado con fondo secondaryContainer
            Row(
                modifier = GlanceModifier
                    .wrapContentWidth()
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = status,
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSecondaryContainer
                    ),
                    maxLines = 1
                )
            }

            // Contador de envios adicionales
            if (extraCount.isNotEmpty()) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = extraCount,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }

        // ── Fila inferior: hora + botones ─────────────────────────────────────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (lastUpdated.isNotEmpty()) lastUpdated else "",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                ),
                modifier = GlanceModifier.defaultWeight()
            )

            // Boton Actualizar — solo Premium
            if (isPremium) {
                Row(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(20.dp)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .clickable(actionRunCallback<RefreshWidgetCallback>())
                ) {
                    Text(
                        text = "Actualizar",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onPrimary
                        )
                    )
                }
                Spacer(GlanceModifier.width(6.dp))
            }

            // Boton Abrir — todos los usuarios
            Row(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Text(
                    text = "Abrir",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onPrimaryContainer
                    )
                )
            }
        }
    }
}

// -- Callback: forzar actualizacion (solo Premium) ----------------------------
class RefreshWidgetCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
            val repo = entryPoint.shipmentRepository()
            val first = repo.activeShipments.first().firstOrNull()
            if (first != null) {
                repo.refreshShipment(first.shipment.id)
            }
        } catch (e: Exception) {
            android.util.Log.w("TrackerWidget", "Error sincronizando desde widget: ${e.message}")
        }
        TrackerWidget().update(context, glanceId)
    }
}
