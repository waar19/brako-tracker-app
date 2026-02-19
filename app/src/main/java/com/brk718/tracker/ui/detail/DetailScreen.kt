package com.brk718.tracker.ui.detail

import android.graphics.Paint
import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.viewinterop.AndroidView
import com.brk718.tracker.data.local.TrackingEventEntity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Tile source minimalista de CartoDB Positron (fondo claro, sin ruido visual)
private val CARTO_POSITRON = object : OnlineTileSourceBase(
    "CartoDB.Positron",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onAmazonAuthClick: (() -> Unit)? = null,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Detalle de Envío") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refresh() },
                            enabled = !isRefreshing
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DetailUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}")
                }
            }
            is DetailUiState.Success -> {
                val shipment = state.shipment.shipment
                val events = state.shipment.events.sortedByDescending { it.timestamp }

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ===== HEADER con fondo de color según estado =====
                    val statusLower = shipment.status.lowercase()
                    val (headerBg, headerContentColor) = when {
                        statusLower == "entregado" ->
                            MaterialTheme.colorScheme.primaryContainer to
                                MaterialTheme.colorScheme.onPrimaryContainer
                        statusLower.contains("error") || statusLower.contains("incidencia") ->
                            MaterialTheme.colorScheme.errorContainer to
                                MaterialTheme.colorScheme.onErrorContainer
                        else ->
                            MaterialTheme.colorScheme.secondaryContainer to
                                MaterialTheme.colorScheme.onSecondaryContainer
                    }

                    Surface(
                        color = headerBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                            Text(
                                text = shipment.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = headerContentColor
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${shipment.carrier} • ${shipment.trackingNumber}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = headerContentColor.copy(alpha = 0.75f)
                            )
                            Spacer(Modifier.height(12.dp))

                            val needsLogin = shipment.status == "LOGIN_REQUIRED" ||
                                shipment.status == "Sign-In required"
                            if (needsLogin && onAmazonAuthClick != null) {
                                Button(onClick = onAmazonAuthClick) {
                                    Text("Reconectar Amazon")
                                }
                            } else {
                                // Badge de estado
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = headerContentColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = shipment.status,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = headerContentColor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ===== MAPA =====
                    val eventsWithLoc = events.filter { it.latitude != null && it.longitude != null }
                    if (eventsWithLoc.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(240.dp),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    Configuration.getInstance().load(
                                        context,
                                        PreferenceManager.getDefaultSharedPreferences(context)
                                    )
                                    MapView(context).apply {
                                        setTileSource(CARTO_POSITRON)
                                        setMultiTouchControls(true)
                                        controller.setZoom(5.0)
                                        // Desactivar UI innecesaria
                                        isTilesScaledToDpi = true
                                    }
                                },
                                update = { mapView ->
                                    mapView.overlays.clear()

                                    val ordered = eventsWithLoc.reversed()
                                    val uniqueOrdered = ordered.distinctBy { Pair(it.latitude, it.longitude) }
                                    val points = uniqueOrdered.map { GeoPoint(it.latitude!!, it.longitude!!) }

                                    // Polilínea
                                    if (points.size >= 2) {
                                        val polyline = Polyline(mapView).apply {
                                            setPoints(points)
                                            outlinePaint.color = android.graphics.Color.parseColor("#2952CC")
                                            outlinePaint.strokeWidth = 7f
                                            outlinePaint.strokeJoin = Paint.Join.ROUND
                                            outlinePaint.strokeCap = Paint.Cap.ROUND
                                            outlinePaint.style = Paint.Style.STROKE
                                            outlinePaint.isAntiAlias = true
                                        }
                                        mapView.overlays.add(polyline)
                                    }

                                    // Marcadores con colores diferenciados
                                    points.forEachIndexed { index, point ->
                                        val marker = Marker(mapView).apply {
                                            position = point
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                            title = uniqueOrdered[index].description
                                            snippet = uniqueOrdered[index].location
                                            // Origen=verde, destino/último=rojo, intermedios=azul
                                            icon = when (index) {
                                                0 -> mapView.context.getDrawable(
                                                    android.R.drawable.presence_online // verde
                                                )
                                                points.lastIndex -> mapView.context.getDrawable(
                                                    android.R.drawable.presence_busy   // rojo
                                                )
                                                else -> mapView.context.getDrawable(
                                                    android.R.drawable.presence_away   // amarillo/intermedio
                                                )
                                            }
                                        }
                                        mapView.overlays.add(marker)
                                    }

                                    if (points.size == 1) {
                                        mapView.controller.setCenter(points.first())
                                        mapView.controller.setZoom(10.0)
                                    } else {
                                        val boundingBox = BoundingBox.fromGeoPoints(points)
                                        mapView.post {
                                            mapView.zoomToBoundingBox(boundingBox, true, 80)
                                        }
                                    }
                                    mapView.invalidate()
                                }
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ===== HISTORIAL =====
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Historial",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            if (events.isEmpty()) {
                                Text(
                                    text = "No hay eventos registrados aún.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column {
                                    events.forEachIndexed { index, event ->
                                        EventItem(
                                            event = event,
                                            isFirst = index == 0,
                                            isLast = index == events.lastIndex
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun EventItem(
    event: TrackingEventEntity,
    isFirst: Boolean,
    isLast: Boolean
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // === Línea de tiempo ===
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(if (isFirst) Color.Transparent else outlineColor)
            )
            Box(
                modifier = Modifier
                    .size(if (isFirst) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (isFirst) primaryColor else outlineColor)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .defaultMinSize(minHeight = 24.dp)
                    .background(if (isLast) Color.Transparent else outlineColor)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // === Contenido ===
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp)
        ) {
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Normal,
                color = if (isFirst) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!event.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .format(Date(event.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
