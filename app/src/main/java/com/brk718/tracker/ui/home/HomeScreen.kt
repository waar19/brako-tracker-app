package com.brk718.tracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.data.local.ShipmentWithEvents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onShipmentClick: (String) -> Unit,
    onGmailClick: () -> Unit = {},
    onAmazonAuthClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val shipments by viewModel.shipments.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Mis Envíos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(
                            onClick = { viewModel.refreshAll() },
                            enabled = !isRefreshing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar todos")
                        }
                        IconButton(onClick = onGmailClick) {
                            Icon(Icons.Default.Email, contentDescription = "Importar de Gmail")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    }
                )
                if (isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo envío") }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        if (shipments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No hay envíos activos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Toca el botón para añadir uno",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(shipments, key = { it.shipment.id }) { item ->
                    ShipmentCard(
                        item = item,
                        onClick = { onShipmentClick(item.shipment.id) },
                        onArchive = { viewModel.archiveShipment(item.shipment.id) },
                        onDelete = { viewModel.deleteShipment(item.shipment.id) },
                        onAmazonAuthClick = onAmazonAuthClick
                    )
                }
            }
        }
    }
}

@Composable
fun ShipmentCard(
    item: ShipmentWithEvents,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAmazonAuthClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val status = item.shipment.status
    val statusLower = status.lowercase()

    val (statusColor, statusContainerColor) = when {
        statusLower == "entregado" ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
        statusLower.contains("error") || statusLower.contains("incidencia") ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        statusLower.contains("manual") || statusLower.contains("no soportado") ->
            MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.surfaceVariant
        else ->
            MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    }

    // Inicial del transportista para el avatar
    val carrierInitial = item.shipment.carrier.firstOrNull()?.uppercaseChar() ?: '?'
    val carrierAvatarColor = when (item.shipment.carrier.lowercase()) {
        "amazon"  -> Color(0xFFFF9900)
        "ups"     -> Color(0xFF351C15)
        "fedex"   -> Color(0xFF4D148C)
        "usps"    -> Color(0xFF004B87)
        "dhl"     -> Color(0xFFFFCC00)
        else      -> MaterialTheme.colorScheme.secondaryContainer
    }
    val carrierTextColor = when (item.shipment.carrier.lowercase()) {
        "dhl"  -> Color(0xFF333333)
        else   -> Color.White
    }

    // Último evento
    val lastEvent = item.events.maxByOrNull { it.timestamp }
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    val needsAmazonLogin = status == "LOGIN_REQUIRED" || status == "Sign-In required" ||
        (item.shipment.trackingNumber.startsWith("111-") && status == "No disponible")

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila superior: avatar + info + menú
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar transportista
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(carrierAvatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = carrierInitial.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = carrierTextColor
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Título + tracking
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.shipment.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.shipment.trackingNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Menú de acciones
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Archivar") },
                            leadingIcon = {
                                Icon(Icons.Default.Archive, null,
                                    tint = MaterialTheme.colorScheme.secondary)
                            },
                            onClick = { menuExpanded = false; onArchive() }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null,
                                    tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Fila inferior: chip de estado + último evento
            if (needsAmazonLogin) {
                Button(
                    onClick = onAmazonAuthClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reconectar Amazon")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chip de estado con fondo
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusContainerColor,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Último evento (fecha)
                    if (lastEvent != null) {
                        Text(
                            text = dateFormat.format(Date(lastEvent.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Último evento descripción
                if (lastEvent != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append(lastEvent.description)
                            if (!lastEvent.location.isNullOrBlank()) {
                                append(" • ")
                                append(lastEvent.location)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
