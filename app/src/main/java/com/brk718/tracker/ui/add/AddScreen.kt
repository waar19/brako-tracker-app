package com.brk718.tracker.ui.add

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.ui.add.AddUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AddShipmentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var trackingNumber by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var carrier by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is AddUiState.Success) {
            onSuccess()
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Envío") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = trackingNumber,
                onValueChange = { trackingNumber = it },
                label = { Text("Número de Seguimiento") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = carrier,
                onValueChange = { carrier = it },
                label = { Text("Transportista (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Si se deja vacío, se intentará detectar automáticamente.") }
            )
            
            if (uiState is AddUiState.Error) {
                Text(
                    text = (uiState as AddUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Button(
                onClick = {
                    viewModel.addShipment(
                        trackingNumber.trim(),
                        title.trim(),
                        carrier.trim().ifBlank { null }
                    )
                },
                enabled = trackingNumber.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is AddUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Guardar Envío")
                }
            }
        }
    }
}
