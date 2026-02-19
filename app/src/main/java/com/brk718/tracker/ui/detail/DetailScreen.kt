package com.brk718.tracker.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.preference.PreferenceManager
import android.graphics.Paint
import com.brk718.tracker.data.local.TrackingEventEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onAmazonAuthClick: (() -> Unit)? = null,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Envío") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
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
                val events = state.shipment.events

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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            AndroidView(
                                factory = { context ->
                                    Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
                                    MapView(context).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(true)
                                        controller.setZoom(10.0)
                                    }
                                },
                                update = { mapView ->
                                    mapView.overlays.clear()
                                    
                                    val points = eventsWithLoc.map { 
                                        GeoPoint(it.latitude!!, it.longitude!!) 
                                    }
                                    
                                    points.forEachIndexed { index, point ->
                                        val marker = Marker(mapView)
                                        marker.position = point
                                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        marker.title = eventsWithLoc[index].description
                                        mapView.overlays.add(marker)
                                    }

                                    if (points.isNotEmpty()) {
                                        mapView.controller.setCenter(points.first()) // Último evento primero
                                    }
                                }
                            )
                        }
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
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(events) { event ->
                                EventItem(event)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventItem(event: TrackingEventEntity) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Línea de tiempo simple (punto y línea vertical se podrían añadir aquí)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            event.location?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(event.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
