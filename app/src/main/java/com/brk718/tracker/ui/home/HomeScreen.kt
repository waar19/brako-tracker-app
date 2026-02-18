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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    viewModel: HomeViewModel = hiltViewModel()
) {
    val shipments by viewModel.shipments.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Mis Envíos") })
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
                        onArchive = { viewModel.archiveShipment(item.shipment.id) }
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
    onArchive: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.shipment.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onArchive) {
                    Icon(Icons.Default.Archive, contentDescription = "Archivar", tint = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${item.shipment.carrier} • ${item.shipment.trackingNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Estado con color semántico básico
            val statusColor = when (item.shipment.status.lowercase()) {
                "entregado" -> MaterialTheme.colorScheme.primary
                "error", "incidencia" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.tertiary
            }
            
            AssistChip(
                onClick = {},
                label = { Text(item.shipment.status) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = statusColor
                ),
                border = BorderStroke(1.dp, statusColor)
            )
        }
    }
}
