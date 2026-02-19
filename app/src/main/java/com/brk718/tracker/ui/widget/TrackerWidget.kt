package com.brk718.tracker.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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

// ── Claves del estado del widget ──────────────────────────────────────────────
private val KEY_IS_PREMIUM       = booleanPreferencesKey("widget_is_premium")
private val KEY_HAS_SHIPMENTS    = booleanPreferencesKey("widget_has_shipments")
private val KEY_SHIPMENT_TITLE   = stringPreferencesKey("widget_title")
private val KEY_SHIPMENT_STATUS  = stringPreferencesKey("widget_status")
private val KEY_SHIPMENT_CARRIER = stringPreferencesKey("widget_carrier")
private val KEY_LAST_UPDATED     = stringPreferencesKey("widget_last_updated")

// ── Entry point Hilt ──────────────────────────────────────────────────────────
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun shipmentRepository(): ShipmentRepository
    fun userPreferencesRepository(): UserPreferencesRepository
}

// ── Widget principal ──────────────────────────────────────────────────────────
class TrackerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val repo      = entryPoint.shipmentRepository()
        val prefsRepo = entryPoint.userPreferencesRepository()

        val prefs        = prefsRepo.preferences.first()
        val isPremium    = prefs.isPremium
        val shipments    = repo.activeShipments.first()
        val latest       = shipments.firstOrNull()
        val hasShipments = shipments.isNotEmpty()

        val dateStr = latest?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.shipment.lastUpdate))
        } ?: ""

        updateAppWidgetState(context, id) { p ->
            p[KEY_IS_PREMIUM]       = isPremium
            p[KEY_HAS_SHIPMENTS]    = hasShipments
            p[KEY_SHIPMENT_TITLE]   = latest?.shipment?.title ?: ""
            p[KEY_SHIPMENT_STATUS]  = latest?.shipment?.status ?: ""
            p[KEY_SHIPMENT_CARRIER] = latest?.let {
                ShipmentRepository.displayName(it.shipment.carrier)
            } ?: ""
            p[KEY_LAST_UPDATED]     = dateStr
        }

        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

// ── Composición del widget ────────────────────────────────────────────────────
@Composable
private fun WidgetContent() {
    val state        = currentState<Preferences>()
    val isPremium    = state[KEY_IS_PREMIUM] ?: false
    val hasShipments = state[KEY_HAS_SHIPMENTS] ?: false
    val title        = state[KEY_SHIPMENT_TITLE] ?: ""
    val status       = state[KEY_SHIPMENT_STATUS] ?: ""
    val carrier      = state[KEY_SHIPMENT_CARRIER] ?: ""
    val lastUpdated  = state[KEY_LAST_UPDATED] ?: ""

    // Delegar a composables separados para evitar problemas con condicionales en Glance
    if (!isPremium) {
        WidgetNotPremium()
    } else if (!hasShipments) {
        WidgetNoShipments()
    } else {
        WidgetShipmentInfo(
            title = title,
            status = status,
            carrier = carrier,
            lastUpdated = lastUpdated
        )
    }
}

// ── Estado: No es Premium ─────────────────────────────────────────────────────
@Composable
private fun WidgetNotPremium() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📦 Brako Tracker",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface
            )
        )
        Spacer(GlanceModifier.height(6))
        Text(
            text = "Solo para usuarios Premium",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
        )
        Spacer(GlanceModifier.height(10))
        Row(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primary)
                .padding(horizontal = 12, vertical = 6)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Text(
                text = "Hazte Premium",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onPrimary
                )
            )
        }
    }
}

// ── Estado: Premium sin envíos ────────────────────────────────────────────────
@Composable
private fun WidgetNoShipments() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📦 Brako Tracker",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface
            )
        )
        Spacer(GlanceModifier.height(6))
        Text(
            text = "Sin envíos activos",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
        )
        Spacer(GlanceModifier.height(10))
        Row(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primaryContainer)
                .padding(horizontal = 12, vertical = 6)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Text(
                text = "Abrir app",
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer)
            )
        }
    }
}

// ── Estado: Premium con envío activo ──────────────────────────────────────────
@Composable
private fun WidgetShipmentInfo(
    title: String,
    status: String,
    carrier: String,
    lastUpdated: String
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12)
    ) {
        // Cabecera
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Brako Tracker",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "✦",
                style = TextStyle(color = GlanceTheme.colors.primary)
            )
        }

        Spacer(GlanceModifier.height(8))

        // Título del envío
        Text(
            text = title,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface
            ),
            maxLines = 1
        )

        // Transportista (solo si no está vacío)
        if (carrier.isNotEmpty()) {
            Text(
                text = carrier,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.height(6))

        // Chip de estado
        Row(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primaryContainer)
                .padding(horizontal = 8, vertical = 4)
        ) {
            Text(
                text = status,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onPrimaryContainer
                ),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.defaultWeight())

        // Fila inferior
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (lastUpdated.isNotEmpty()) "Act: $lastUpdated" else "",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.defaultWeight()
            )
            Row(
                modifier = GlanceModifier
                    .padding(end = 10)
                    .clickable(actionRunCallback<RefreshWidgetCallback>())
            ) {
                Text(
                    text = "Actualizar",
                    style = TextStyle(color = GlanceTheme.colors.primary)
                )
            }
            Row(
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
            ) {
                Text(
                    text = "Abrir",
                    style = TextStyle(color = GlanceTheme.colors.primary)
                )
            }
        }
    }
}

// ── Callback: forzar actualización ───────────────────────────────────────────
class RefreshWidgetCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        TrackerWidget().update(context, glanceId)
    }
}
