package com.brk718.tracker.ui.detail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint as AndroidPaint
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.compose.OnParticleSystemUpdateListener
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.PartySystem
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.brk718.tracker.util.ShareCardGenerator
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.R
import androidx.compose.ui.viewinterop.AndroidView
import com.brk718.tracker.data.local.TrackingEventEntity
import com.brk718.tracker.data.repository.ShipmentRepository
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
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

/** Crea un marcador circular dibujado con Canvas, sin depender de drawables del sistema */
private fun makeCircleMarker(color: Int, sizePx: Int = 36, strokeColor: Int = android.graphics.Color.WHITE): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        this.color = color
    }
    val stroke = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        this.color = strokeColor
        strokeWidth = sizePx * 0.12f
    }
    val r = sizePx / 2f
    canvas.drawCircle(r, r, r - stroke.strokeWidth, paint)
    canvas.drawCircle(r, r, r - stroke.strokeWidth, stroke)
    return bitmap
}

/** Configura los overlays del mapa (polilínea + marcadores) */
private fun setupMapOverlays(
    mapView: MapView,
    points: List<GeoPoint>,
    events: List<TrackingEventEntity>
) {
    mapView.overlays.clear()

    // Desactivar controles de zoom built-in de OSMDroid (los botones +/-)
    mapView.setBuiltInZoomControls(false)

    // Polilínea
    if (points.size >= 2) {
        val polyline = Polyline(mapView).apply {
            setPoints(points)
            outlinePaint.color = android.graphics.Color.parseColor("#2952CC")
            outlinePaint.strokeWidth = 7f
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.style = android.graphics.Paint.Style.STROKE
            outlinePaint.isAntiAlias = true
        }
        mapView.overlays.add(polyline)
    }

    // Marcadores: verde=origen, azul=intermedios, rojo=destino actual
    val colorOrigin       = android.graphics.Color.parseColor("#22C55E")  // verde
    val colorIntermediate = android.graphics.Color.parseColor("#3B82F6")  // azul
    val colorDestination  = android.graphics.Color.parseColor("#EF4444")  // rojo

    points.forEachIndexed { index, point ->
        val markerColor = when (index) {
            0                -> colorOrigin
            points.lastIndex -> colorDestination
            else             -> colorIntermediate
        }
        val sizePx = if (index == 0 || index == points.lastIndex) 42 else 30

        val bitmapDrawable = android.graphics.drawable.BitmapDrawable(
            mapView.resources,
            makeCircleMarker(markerColor, sizePx)
        )

        val marker = Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = bitmapDrawable
            // Desactivar el InfoWindow de OSMDroid (evita el popup con +/- al hacer click)
            infoWindow = null
        }
        mapView.overlays.add(marker)
    }

    // Ajustar zoom
    if (points.size == 1) {
        mapView.controller.setCenter(points.first())
        mapView.controller.setZoom(10.0)
    } else {
        val boundingBox = BoundingBox.fromGeoPoints(points)
        mapView.post {
            mapView.zoomToBoundingBox(boundingBox, true, 100)
        }
    }
    mapView.invalidate()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onAmazonAuthClick: (() -> Unit)? = null,
    onUpgradeClick: (() -> Unit)? = null,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Estado para el diálogo del mapa
    var showMapDialog by remember { mutableStateOf(false) }

    // Estado para el diálogo de editar título
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var editTitleText by remember { mutableStateOf("") }

    // Confetti: mostrar una vez cuando el estado es "entregado"
    var confettiShown by remember { mutableStateOf(false) }
    val isDelivered = (uiState as? DetailUiState.Success)
        ?.shipment?.shipment?.status?.lowercase()?.contains("entregado") == true
    val showConfetti = isDelivered && !confettiShown

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.detail_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back))
                        }
                    },
                    actions = {
                        // Botón de compartir — solo visible cuando hay datos cargados
                        if (uiState is DetailUiState.Success) {
                            IconButton(onClick = {
                                ShareCardGenerator.shareShipmentAsImage(
                                    context,
                                    (uiState as DetailUiState.Success).shipment
                                )
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Compartir estado del envío"
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.refresh() },
                            enabled = !isRefreshing
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.detail_refresh))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (isRefreshing || uiState is DetailUiState.Loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                // La barra de progreso ya se muestra en el TopAppBar — nada más aquí
            }
            is DetailUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.detail_error_prefix) + state.message)
                }
            }
            is DetailUiState.Success -> {
                val shipment = state.shipment.shipment
                val events = state.shipment.events.sortedByDescending { it.timestamp }
                val historyLimited = state.historyLimited
                val hiddenEventCount = state.hiddenEventCount

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
                            // Título + ícono de editar
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = shipment.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = headerContentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        editTitleText = shipment.title
                                        showEditTitleDialog = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Editar título",
                                        modifier = Modifier.size(18.dp),
                                        tint = headerContentColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            // Carrier + tracking number + ícono de copiar
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${ShipmentRepository.displayName(shipment.carrier)} - ${shipment.trackingNumber}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = headerContentColor.copy(alpha = 0.75f),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(shipment.trackingNumber))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copiar número de tracking",
                                        modifier = Modifier.size(16.dp),
                                        tint = headerContentColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            val needsLogin = shipment.status == "LOGIN_REQUIRED" ||
                                shipment.status == "Sign-In required"
                            if (needsLogin && onAmazonAuthClick != null) {
                                Button(onClick = onAmazonAuthClick) {
                                    Text(stringResource(R.string.detail_reconnect_amazon))
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
                        val orderedEvents = eventsWithLoc.reversed()
                        val uniqueEvents = orderedEvents.distinctBy { Pair(it.latitude, it.longitude) }
                        val mapPoints = uniqueEvents.map { GeoPoint(it.latitude!!, it.longitude!!) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(240.dp)
                                .clickable { showMapDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        Configuration.getInstance().load(
                                            ctx,
                                            ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                                        )
                                        MapView(ctx).apply {
                                            setTileSource(CARTO_POSITRON)
                                            setMultiTouchControls(false) // desactivado en preview
                                            controller.setZoom(5.0)
                                            isTilesScaledToDpi = true
                                            isClickable = false
                                        }
                                    },
                                    update = { mapView ->
                                        setupMapOverlays(mapView, mapPoints, uniqueEvents)
                                    }
                                )

                                // Botón "Ampliar" superpuesto
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    shadowElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Map,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            stringResource(R.string.detail_expand_map),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Leyenda con etiquetas en esquina inferior derecha
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    shadowElevation = 2.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        LegendDot(Color(0xFF22C55E), stringResource(R.string.detail_map_origin))
                                        LegendDot(Color(0xFF3B82F6), stringResource(R.string.detail_map_transit))
                                        LegendDot(Color(0xFFEF4444), stringResource(R.string.detail_map_current_location))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        // ===== DIÁLOGO MAPA AMPLIADO =====
                        if (showMapDialog) {
                            Dialog(
                                onDismissRequest = { showMapDialog = false },
                                properties = DialogProperties(
                                    usePlatformDefaultWidth = false,
                                    dismissOnBackPress = true,
                                    dismissOnClickOutside = false,
                                    decorFitsSystemWindows = false
                                )
                            ) {
                                // Referencia al MapView para los botones de zoom propios
                                var expandedMapView by remember { mutableStateOf<MapView?>(null) }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    // Mapa edge-to-edge (sin padding — se extiende detrás de barras)
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize(),
                                        factory = { ctx ->
                                            Configuration.getInstance().load(
                                                ctx,
                                                ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                                            )
                                            MapView(ctx).apply {
                                                setTileSource(CARTO_POSITRON)
                                                setMultiTouchControls(true)
                                                controller.setZoom(5.0)
                                                isTilesScaledToDpi = true
                                            }
                                        },
                                        update = { mapView ->
                                            expandedMapView = mapView
                                            setupMapOverlays(mapView, mapPoints, uniqueEvents)
                                        }
                                    )

                                    // Capa de overlays: ocupa todo el espacio visible seguro.
                                    // safeDrawingPadding() aplica statusBar + navigationBar + displayCutout
                                    // de una vez, garantizando que los controles queden dentro del
                                    // área visible independientemente del tipo de barra (gestos o botones).
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .safeDrawingPadding()
                                    ) {
                                        // Botón cerrar (esquina superior derecha)
                                        IconButton(
                                            onClick = { showMapDialog = false },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.detail_map_close),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        // Leyenda (esquina inferior derecha)
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(16.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            shadowElevation = 4.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                LegendDot(Color(0xFF22C55E), stringResource(R.string.detail_map_origin))
                                                LegendDot(Color(0xFF3B82F6), stringResource(R.string.detail_map_transit))
                                                LegendDot(Color(0xFFEF4444), stringResource(R.string.detail_map_current_location))
                                            }
                                        }

                                        // Controles de zoom (esquina inferior izquierda)
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilledTonalIconButton(
                                                onClick = { expandedMapView?.controller?.zoomIn() },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Acercar",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            FilledTonalIconButton(
                                                onClick = { expandedMapView?.controller?.zoomOut() },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Remove,
                                                    contentDescription = "Alejar",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ===== HISTORIAL =====
                    val maxCollapsed = 3
                    var historialExpanded by remember { mutableStateOf(false) }

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
                            // Cabecera con contador
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.detail_history_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (events.size > maxCollapsed) {
                                    Text(
                                        text = stringResource(R.string.detail_events_count, events.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            if (events.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Text(
                                        text = "Aun no hay eventos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "El transportista aun no registro movimientos. Vuelve a revisar en un rato.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                // Primeros N eventos siempre visibles
                                Column {
                                    val alwaysVisible = events.take(minOf(maxCollapsed, events.size))
                                    alwaysVisible.forEachIndexed { index, event ->
                                        EventItem(
                                            event = event,
                                            isFirst = index == 0,
                                            isLast = isLast(
                                                index = index,
                                                alwaysVisibleSize = alwaysVisible.size,
                                                totalSize = events.size,
                                                maxCollapsed = maxCollapsed,
                                                expanded = historialExpanded
                                            )
                                        )
                                    }
                                }

                                // Eventos extra — animados
                                if (events.size > maxCollapsed) {
                                    AnimatedVisibility(
                                        visible = historialExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column {
                                            events.drop(maxCollapsed).forEachIndexed { index, event ->
                                                EventItem(
                                                    event = event,
                                                    isFirst = false,
                                                    isLast = index == events.size - maxCollapsed - 1
                                                )
                                            }
                                        }
                                    }

                                    // Botón expandir/colapsar
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { historialExpanded = !historialExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = if (historialExpanded)
                                                Icons.Default.KeyboardArrowUp
                                            else
                                                Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (historialExpanded)
                                                stringResource(R.string.detail_show_less)
                                            else
                                                stringResource(R.string.detail_show_all, events.size),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Banner historial limitado (solo free)
                    if (historyLimited) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (hiddenEventCount > 0)
                                            "$hiddenEventCount evento${if (hiddenEventCount == 1) "" else "s"} oculto${if (hiddenEventCount == 1) "" else "s"}"
                                        else
                                            "Historial de $FREE_HISTORY_DAYS días",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "Solo ves los últimos $FREE_HISTORY_DAYS días — Premium muestra todo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                if (onUpgradeClick != null) {
                                    FilledTonalButton(
                                        onClick = onUpgradeClick,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Premium", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }

                // ===== DIÁLOGO EDITAR TÍTULO =====
                if (showEditTitleDialog) {
                    AlertDialog(
                        onDismissRequest = { showEditTitleDialog = false },
                        title = { Text("Editar nombre") },
                        text = {
                            OutlinedTextField(
                                value = editTitleText,
                                onValueChange = { editTitleText = it },
                                label = { Text("Nombre del envio") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.updateTitle(editTitleText)
                                    showEditTitleDialog = false
                                },
                                enabled = editTitleText.isNotBlank()
                            ) {
                                Text("Guardar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEditTitleDialog = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }
            }
        }
        // Confetti superpuesto al contenido (solo cuando entregado, una vez)
        if (showConfetti) {
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(
                    Party(
                        emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(80),
                        position = Position.Relative(0.5, 0.0)
                    )
                ),
                updateListener = object : OnParticleSystemUpdateListener {
                    override fun onParticleSystemEnded(system: PartySystem, activeSystems: Int) {
                        if (activeSystems == 0) confettiShown = true
                    }
                }
            )
        }
        } // cierre Box
    }
}

/** Calcula si un evento visible es el último de la línea de tiempo visible */
private fun isLast(
    index: Int,
    alwaysVisibleSize: Int,
    totalSize: Int,
    maxCollapsed: Int,
    expanded: Boolean
): Boolean {
    return when {
        totalSize <= maxCollapsed -> index == totalSize - 1       // todos visibles
        expanded                 -> false                          // hay más abajo expandidos
        else                     -> index == alwaysVisibleSize - 1 // último de los colapsados
    }
}

/** Punto de leyenda con etiqueta */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
