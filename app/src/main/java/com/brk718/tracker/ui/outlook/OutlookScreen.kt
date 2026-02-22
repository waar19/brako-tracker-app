package com.brk718.tracker.ui.outlook

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.domain.ParsedShipment
import com.brk718.tracker.ui.add.FREE_SHIPMENT_LIMIT

/** Color corporativo de Microsoft / Outlook */
private val OutlookBlue = Color(0xFF0078D4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlookScreen(
    onBack: () -> Unit,
    viewModel: OutlookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Outlook / Hotmail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (uiState.isConnected) {
                        IconButton(onClick = { viewModel.scanEmails() }) {
                            Icon(Icons.Default.Refresh, "Escanear emails")
                        }
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Desconectar Outlook",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                // ── No conectado ──────────────────────────────────────────────
                !uiState.isConnected -> {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = OutlookBlue
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Conecta tu Outlook",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Escanea tus correos de Outlook u Hotmail para detectar guías de envío automáticamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    if (uiState.isConnecting) {
                        CircularProgressIndicator(color = OutlookBlue)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Conectando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = { viewModel.signIn(context as Activity) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OutlookBlue
                            )
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conectar Outlook / Hotmail")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                // ── Escaneando ────────────────────────────────────────────────
                uiState.isScanning -> {
                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressIndicator(color = OutlookBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Escaneando correos...")
                    Spacer(modifier = Modifier.weight(1f))
                }

                // ── Sin resultados (ya escaneó) ────────────────────────────────
                uiState.foundShipments.isEmpty() && uiState.hasScanned -> {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "No se encontraron guías",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No detectamos números de seguimiento en tus últimos 30 días de correos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.scanEmails() }) {
                        Text("Volver a escanear")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                // ── Resultados ────────────────────────────────────────────────
                else -> {
                    Text(
                        "Cuenta: ${uiState.accountEmail ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!uiState.hasScanned) {
                        Button(onClick = { viewModel.scanEmails() }) {
                            Text("Escanear ahora")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (uiState.foundShipments.isNotEmpty()) {
                        Text(
                            "${uiState.foundShipments.size} guía(s) encontradas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Banner de límite alcanzado (tier free)
                        if (uiState.limitReachedIds.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Límite de $FREE_SHIPMENT_LIMIT envíos activos alcanzado",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            "Hazte Premium para importar sin límites",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Botón "Importar todos" — solo si hay pendientes sin importar
                        val pendingCount = uiState.foundShipments
                            .count { it.trackingNumber !in uiState.importedIds }
                        if (pendingCount > 0) {
                            FilledTonalButton(
                                onClick = { viewModel.importAll() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Importar todos ($pendingCount)")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.foundShipments) { shipment ->
                                OutlookDetectedShipmentCard(
                                    shipment = shipment,
                                    isImported = shipment.trackingNumber in uiState.importedIds,
                                    isImporting = shipment.trackingNumber in uiState.importingIds,
                                    isLimitReached = shipment.trackingNumber in uiState.limitReachedIds,
                                    onImport = { viewModel.importShipment(shipment) }
                                )
                            }
                        }
                    }
                }
            }

            // Error
            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun OutlookDetectedShipmentCard(
    shipment: ParsedShipment,
    isImported: Boolean,
    isImporting: Boolean,
    isLimitReached: Boolean,
    onImport: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shipment.title ?: shipment.trackingNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    "${shipment.carrier} • ${shipment.trackingNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            when {
                isImported -> AssistChip(
                    onClick = {},
                    label = { Text("Importado") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                )
                isImporting -> FilledTonalButton(onClick = {}, enabled = false) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importando...")
                }
                isLimitReached -> AssistChip(
                    onClick = {},
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Premium")
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error
                    )
                )
                else -> FilledTonalButton(onClick = onImport) {
                    Text("Importar")
                }
            }
        }
    }
}
