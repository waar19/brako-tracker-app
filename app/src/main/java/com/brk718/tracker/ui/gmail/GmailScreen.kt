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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Lock
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.R
import com.brk718.tracker.domain.ParsedShipment
import com.brk718.tracker.ui.add.FREE_SHIPMENT_LIMIT

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
                title = { Text(stringResource(R.string.gmail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.gmail_back))
                    }
                },
                actions = {
                    if (uiState.isConnected) {
                        IconButton(onClick = { viewModel.scanEmails() }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.gmail_scan))
                        }
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.gmail_disconnect),
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
                    stringResource(R.string.gmail_connect_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.gmail_connect_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { signInLauncher.launch(viewModel.getSignInIntent()) }
                ) {
                    Text(stringResource(R.string.gmail_connect_button))
                }
                Spacer(modifier = Modifier.weight(1f))
            } else if (uiState.isScanning) {
                // Escaneando
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.gmail_scanning))
                Spacer(modifier = Modifier.weight(1f))
            } else if (uiState.foundShipments.isEmpty() && uiState.hasScanned) {
                // Sin resultados
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.gmail_no_results_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.gmail_no_results_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = { viewModel.scanEmails() }) {
                    Text(stringResource(R.string.gmail_rescan))
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
                        Text(stringResource(R.string.gmail_scan_button))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (uiState.foundShipments.isNotEmpty()) {
                    Text(
                        stringResource(R.string.gmail_found_count, uiState.foundShipments.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Banner de límite alcanzado (tier free)
                    val anyLimitReached = uiState.limitReachedIds.isNotEmpty()
                    if (anyLimitReached) {
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
                            DetectedShipmentCard(
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
                    label = { Text(stringResource(R.string.gmail_imported_chip)) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                )
                isImporting -> FilledTonalButton(
                    onClick = {},
                    enabled = false
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.gmail_importing_button))
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
                    Text(stringResource(R.string.gmail_import_button))
                }
            }
        }
    }
}
