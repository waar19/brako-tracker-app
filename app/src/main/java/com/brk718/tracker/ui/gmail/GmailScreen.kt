package com.brk718.tracker.ui.gmail

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.domain.ParsedShipment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GmailScreen(
    onBack: () -> Unit,
    viewModel: GmailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleSignInResult(result.data)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Importar de Gmail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                actions = {
                    if (uiState.isConnected) {
                        IconButton(onClick = { viewModel.scanEmails() }) {
                            Icon(Icons.Default.Refresh, "Escanear")
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
            if (!uiState.isConnected) {
                // No conectado — mostrar botón de sign-in
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Conecta tu cuenta de Gmail",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Escanearemos tus emails de envío para detectar tracking numbers automáticamente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { signInLauncher.launch(viewModel.getSignInIntent()) }
                ) {
                    Text("Conectar Gmail")
                }
                Spacer(modifier = Modifier.weight(1f))
            } else if (uiState.isScanning) {
                // Escaneando
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Escaneando emails de envío...")
                Spacer(modifier = Modifier.weight(1f))
            } else if (uiState.foundShipments.isEmpty() && uiState.hasScanned) {
                // Sin resultados
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "No se encontraron tracking numbers",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Intenta escanear de nuevo o añade manualmente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = { viewModel.scanEmails() }) {
                    Text("Escanear de nuevo")
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                // Resultados
                Text(
                    "Cuenta: ${uiState.accountEmail ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (!uiState.hasScanned) {
                    Button(onClick = { viewModel.scanEmails() }) {
                        Text("Escanear emails de envío")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (uiState.foundShipments.isNotEmpty()) {
                    Text(
                        "${uiState.foundShipments.size} envíos detectados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.foundShipments) { shipment ->
                            DetectedShipmentCard(
                                shipment = shipment,
                                isImported = shipment.trackingNumber in uiState.importedIds,
                                onImport = { viewModel.importShipment(shipment) }
                            )
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
fun DetectedShipmentCard(
    shipment: ParsedShipment,
    isImported: Boolean,
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
            if (isImported) {
                AssistChip(
                    onClick = {},
                    label = { Text("Importado") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                FilledTonalButton(onClick = onImport) {
                    Text("Importar")
                }
            }
        }
    }
}
