package com.brk718.tracker.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// Detecta el transportista en base al formato del número de tracking
private fun detectCarrier(tracking: String): String? {
    val t = tracking.trim()
    return when {
        // Amazon: 111-XXXXXXX-XXXXXXX o TBA + dígitos
        t.matches(Regex("\\d{3}-\\d{7}-\\d{7}")) -> "Amazon"
        t.startsWith("TBA", ignoreCase = true) && t.length >= 12 -> "Amazon"
        // UPS: empieza con 1Z + 16 caracteres alfanuméricos
        t.startsWith("1Z", ignoreCase = true) && t.length >= 18 -> "UPS"
        // FedEx: 12 dígitos, 15 dígitos o empieza con 96/94
        t.matches(Regex("\\d{12}")) -> "FedEx"
        t.matches(Regex("\\d{15}")) -> "FedEx"
        t.startsWith("96") && t.length >= 20 -> "FedEx"
        t.startsWith("94") && t.length >= 20 -> "USPS"
        // USPS: 20-22 dígitos o empieza con 9400/9205/9361
        t.matches(Regex("\\d{20,22}")) -> "USPS"
        t.startsWith("9400") || t.startsWith("9205") || t.startsWith("9361") -> "USPS"
        // DHL: empieza con JD o tiene exactamente 10 dígitos
        t.startsWith("JD", ignoreCase = true) && t.length >= 10 -> "DHL"
        t.matches(Regex("\\d{10}")) -> "DHL"
        // Carriers colombianos — guías numéricas típicas
        // Interrapidísimo: 12 dígitos que comienzan con 2400, 2401, 2402, 2403
        t.matches(Regex("24\\d{10}")) -> "Interrapidísimo"
        // Coordinadora: 10 dígitos que comienzan con 5, 6, 7 u 8
        t.matches(Regex("[5-8]\\d{9}")) -> "Coordinadora"
        // Servientrega: 10-11 dígitos que comienzan con 9
        t.matches(Regex("9\\d{9,10}")) -> "Servientrega"
        // Envía / Colvanes: 12-13 dígitos que comienzan con 1
        t.matches(Regex("1\\d{11,12}")) -> "Envía"
        else -> null
    }
}

private fun carrierColor(carrier: String): Color = when (carrier.lowercase()) {
    "amazon"          -> Color(0xFFFF9900)
    "ups"             -> Color(0xFF351C15)
    "fedex"           -> Color(0xFF4D148C)
    "usps"            -> Color(0xFF004B87)
    "dhl"             -> Color(0xFFFFCC00)
    "interrapidísimo" -> Color(0xFFE30613) // rojo corporativo Interrapidísimo
    "coordinadora"    -> Color(0xFF003087) // azul Coordinadora
    "servientrega"    -> Color(0xFF009B48) // verde Servientrega
    "envía"           -> Color(0xFFFF6B00) // naranja Envía
    else              -> Color(0xFF535D7E)
}

private fun carrierTextColor(carrier: String): Color = when (carrier.lowercase()) {
    "dhl" -> Color(0xFF1A1A1A)
    else  -> Color.White
}

private fun carrierHint(carrier: String): String = when (carrier.lowercase()) {
    "amazon"          -> "Formato: 111-XXXXXXX-XXXXXXX o TBA..."
    "ups"             -> "Formato: 1Z + 16 caracteres"
    "fedex"           -> "Formato: 12 o 15 dígitos"
    "usps"             -> "Formato: 20-22 dígitos"
    "dhl"             -> "Formato: JD... o 10 dígitos"
    "interrapidísimo" -> "Formato: 24 + 10 dígitos (ej: 240046650823)"
    "coordinadora"    -> "Formato: 10 dígitos (ej: 5XXXXXXXXX)"
    "servientrega"    -> "Formato: 10-11 dígitos (ej: 9XXXXXXXXX)"
    "envía"           -> "Formato: 12-13 dígitos (ej: 1XXXXXXXXXXXX)"
    else              -> ""
}

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

    // Detección automática del transportista al escribir
    val detectedCarrier by remember(trackingNumber) {
        derivedStateOf { detectCarrier(trackingNumber) }
    }

    LaunchedEffect(uiState) {
        if (uiState is AddUiState.Success) {
            onSuccess()
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Nuevo Envío",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === Campo tracking con badge detectado ===
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = trackingNumber,
                    onValueChange = { trackingNumber = it },
                    label = { Text("Número de Seguimiento") },
                    placeholder = { Text("Ej: 111-XXXXXXX-XXXXXXX") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = if (detectedCarrier != null) {
                        { Text(carrierHint(detectedCarrier!!), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        { Text("Amazon, UPS, FedEx, USPS, DHL") }
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = detectedCarrier != null,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut()
                        ) {
                            if (detectedCarrier != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = carrierColor(detectedCarrier!!),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = detectedCarrier!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = carrierTextColor(detectedCarrier!!),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título (opcional)") },
                placeholder = { Text("Ej: Zapatillas Nike, Pedido Amazon...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = carrier,
                onValueChange = { carrier = it },
                label = { Text("Transportista (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        if (detectedCarrier != null) "Detectado: $detectedCarrier"
                        else "Auto-detectado si se deja vacío"
                    )
                },
                supportingText = { Text("Deja vacío para usar la detección automática") }
            )

            // === Error ===
            if (uiState is AddUiState.Error) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = (uiState as AddUiState.Error).message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // === Botón guardar ===
            Button(
                onClick = {
                    val effectiveCarrier = carrier.trim().ifBlank { detectedCarrier }
                    viewModel.addShipment(
                        trackingNumber.trim(),
                        title.trim(),
                        effectiveCarrier
                    )
                },
                enabled = trackingNumber.isNotBlank() && uiState !is AddUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (uiState is AddUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        "Guardar Envío",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
