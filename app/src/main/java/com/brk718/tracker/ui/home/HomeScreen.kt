package com.brk718.tracker.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.data.local.ShipmentWithEvents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onShipmentClick: (String) -> Unit,
    onGmailClick: () -> Unit = {},
    onAmazonAuthClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val shipments by viewModel.shipments.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mis Envíos") },
                actions = {
                    IconButton(onClick = onGmailClick) {
                        Icon(Icons.Default.Email, contentDescription = "Importar de Gmail")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Añadir")
            }
        }
    ) { padding ->
        if (shipments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay envíos activos. ¡Añade uno!")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = item.shipment.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${item.shipment.carrier} • ${item.shipment.trackingNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Estado + acciones
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Estado con color semántico
                val status = item.shipment.status.lowercase()
                val statusColor = when {
                    status == "entregado" -> MaterialTheme.colorScheme.primary
                    status.contains("error") || status.contains("incidencia") -> MaterialTheme.colorScheme.error
                    status.contains("manual") || status.contains("no soportado") -> MaterialTheme.colorScheme.outline
                    status.contains("registrando") -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.tertiary
                }
                
                if (item.shipment.status == "LOGIN_REQUIRED" || 
                    (item.shipment.trackingNumber.startsWith("111-") && item.shipment.status == "No disponible")) {
                    Button(
                        onClick = onAmazonAuthClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (item.shipment.status == "No disponible") "Reconectar Amazon" else "Conectar Amazon")
                    }
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text(item.shipment.status) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = statusColor
                        ),
                        border = BorderStroke(1.dp, statusColor)
                    )
                }
                
                Row {
                    IconButton(onClick = onArchive) {
                        Icon(Icons.Default.Archive, contentDescription = "Archivar",
                            tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
