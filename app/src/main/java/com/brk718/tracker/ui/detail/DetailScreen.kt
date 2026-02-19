package com.brk718.tracker.ui.detail

import android.graphics.Paint
import android.preference.PreferenceManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.viewinterop.AndroidView
import com.brk718.tracker.data.local.TrackingEventEntity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

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
                            Icon(Icons.Filled.ArrowBack, "Atrás")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refresh() },
                            enabled = !isRefreshing
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                        }
                    }
                )
                // Barra de progreso debajo del TopAppBar mientras refresca (sin ocultar contenido)
                if (isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    ) { padding ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DetailUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Error: ${state.message}")
                }
            }
            is DetailUiState.Success -> {
                val shipment = state.shipment.shipment
                // Ordenar más reciente primero (Room @Relation no garantiza orden)
                val events = state.shipment.events.sortedByDescending { it.timestamp }

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Text(
                        text = shipment.title,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${shipment.carrier} • ${shipment.trackingNumber}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val statusColor = when (shipment.status.lowercase()) {
                        "entregado" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                    
                    val needsLogin = shipment.status == "LOGIN_REQUIRED" ||
                        shipment.status == "Sign-In required"
                    if (needsLogin && onAmazonAuthClick != null) {
                        Button(onClick = onAmazonAuthClick) {
                            Text("Reconectar Amazon")
                        }
                    } else {
                        AssistChip(
                            onClick = {},
                            label = { Text(shipment.status) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = statusColor),
                            border = BorderStroke(1.dp, statusColor)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Mapa
                    val eventsWithLoc = events.filter { it.latitude != null && it.longitude != null }
                    if (eventsWithLoc.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
                                    MapView(context).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(true)
                                        controller.setZoom(5.0)
                                    }
                                },
                                update = { mapView ->
                                    mapView.overlays.clear()

                                    // events ya viene ordenado DESC por timestamp (más reciente primero).
                                    // eventsWithLoc hereda ese orden. Invertimos para orden cronológico ASC.
                                    val ordered = eventsWithLoc.reversed() // más antiguo → más reciente

                                    // Deduplicar puntos consecutivos con las mismas coordenadas
                                    // (misma ciudad aparece varias veces → segmentos de longitud 0)
                                    val uniqueOrdered = ordered.distinctBy {
                                        Pair(it.latitude, it.longitude)
                                    }
                                    val points = uniqueOrdered.map { GeoPoint(it.latitude!!, it.longitude!!) }

                                    // Polilínea que conecta todos los puntos en orden (línea abierta)
                                    if (points.size >= 2) {
                                        val polyline = Polyline(mapView).apply {
                                            setPoints(points)
                                            outlinePaint.color = android.graphics.Color.parseColor("#1976D2")
                                            outlinePaint.strokeWidth = 6f
                                            outlinePaint.strokeJoin = Paint.Join.ROUND
                                            outlinePaint.strokeCap = Paint.Cap.ROUND
                                            // Style.STROKE evita que se rellene y se cierre como polígono
                                            outlinePaint.style = android.graphics.Paint.Style.STROKE
                                        }
                                        mapView.overlays.add(polyline)
                                    }

                                    // Marcadores para cada ciudad única
                                    points.forEachIndexed { index, point ->
                                        val marker = Marker(mapView).apply {
                                            position = point
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = uniqueOrdered[index].description
                                            snippet = uniqueOrdered[index].location
                                        }
                                        mapView.overlays.add(marker)
                                    }

                                    // Centrar y hacer zoom para encuadrar todos los puntos
                                    if (points.size == 1) {
                                        mapView.controller.setCenter(points.first())
                                        mapView.controller.setZoom(10.0)
                                    } else {
                                        val boundingBox = BoundingBox.fromGeoPoints(points)
                                        mapView.post {
                                            mapView.zoomToBoundingBox(boundingBox, true, 60)
                                        }
                                    }

                                    mapView.invalidate()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = "Historial",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (events.isEmpty()) {
                        Text(
                            text = "No hay eventos registrados aún.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Timeline visual
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
        // === Columna de la línea de tiempo (punto + línea) ===
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            // Línea superior (invisible para el primer elemento)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(if (isFirst) androidx.compose.ui.graphics.Color.Transparent else outlineColor)
            )
            // Punto del evento (más grande y coloreado si es el más reciente)
            Box(
                modifier = Modifier
                    .size(if (isFirst) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (isFirst) primaryColor else outlineColor)
            )
            // Línea inferior (invisible para el último elemento)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .defaultMinSize(minHeight = 24.dp)
                    .background(if (isLast) androidx.compose.ui.graphics.Color.Transparent else outlineColor)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // === Contenido del evento ===
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
                text = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(event.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
