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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.R
import com.brk718.tracker.ui.add.FREE_SHIPMENT_LIMIT

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
        // Interrapidísimo: 12 dígitos que comienzan con 24
        t.matches(Regex("24\\d{10}")) -> "Interrapidísimo"
        // Coordinadora: 10 dígitos que comienzan con 5, 6, 7 u 8
        t.matches(Regex("[5-8]\\d{9}")) -> "Coordinadora"
        // Servientrega: 10-11 dígitos que comienzan con 9
        t.matches(Regex("9\\d{9,10}")) -> "Servientrega"
        // Envía / Colvanes: 12-13 dígitos que comienzan con 1
        t.matches(Regex("1\\d{11,12}")) -> "Envía"
        // Listo: empieza con L + 8-12 dígitos
        t.matches(Regex("L\\d{8,12}", RegexOption.IGNORE_CASE)) -> "Listo"
        // Treda: empieza con T + 9 dígitos
        t.matches(Regex("T\\d{9}", RegexOption.IGNORE_CASE)) -> "Treda"
        // Speed Colombia: empieza con S + 10 dígitos
        t.matches(Regex("S\\d{10}", RegexOption.IGNORE_CASE)) -> "Speed"
        // Castores: empieza con C + 9-10 dígitos
        t.matches(Regex("C\\d{9,10}", RegexOption.IGNORE_CASE)) -> "Castores"
        // Avianca Cargo: 11 dígitos que comienzan con 134
        t.matches(Regex("134\\d{8}")) -> "Avianca Cargo"
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
    "listo"           -> Color(0xFF00B4D8) // azul claro Listo
    "treda"           -> Color(0xFF7209B7) // morado Treda
    "speed"           -> Color(0xFFE63946) // rojo Speed
    "castores"        -> Color(0xFF2D6A4F) // verde oscuro Castores
    "avianca cargo"   -> Color(0xFFD62828) // rojo Avianca
    else              -> Color(0xFF535D7E)
}

private fun carrierTextColor(carrier: String): Color = when (carrier.lowercase()) {
    "dhl" -> Color(0xFF1A1A1A)
    else  -> Color.White
}

@Composable
private fun carrierHint(carrier: String): String = when (carrier.lowercase()) {
    "amazon"          -> stringResource(R.string.add_hint_amazon)
    "ups"             -> stringResource(R.string.add_hint_ups)
    "fedex"           -> stringResource(R.string.add_hint_fedex)
    "usps"            -> stringResource(R.string.add_hint_usps)
    "dhl"             -> stringResource(R.string.add_hint_dhl)
    "interrapidísimo" -> stringResource(R.string.add_hint_interrapidisimo)
    "coordinadora"    -> stringResource(R.string.add_hint_coordinadora)
    "servientrega"    -> stringResource(R.string.add_hint_servientrega)
    "envía"           -> stringResource(R.string.add_hint_envia)
    else              -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onUpgradeClick: () -> Unit = onBack,
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
                        stringResource(R.string.add_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.add_back))
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
                    label = { Text(stringResource(R.string.add_tracking_label)) },
                    placeholder = { Text(stringResource(R.string.add_tracking_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = if (detectedCarrier != null) {
                        { Text(carrierHint(detectedCarrier!!), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        { Text(stringResource(R.string.add_tracking_hint_default)) }
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
                label = { Text(stringResource(R.string.add_title_label)) },
                placeholder = { Text(stringResource(R.string.add_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = carrier,
                onValueChange = { carrier = it },
                label = { Text(stringResource(R.string.add_carrier_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        if (detectedCarrier != null)
                            stringResource(R.string.add_carrier_placeholder_detected, detectedCarrier!!)
                        else
                            stringResource(R.string.add_carrier_placeholder_auto)
                    )
                },
                supportingText = { Text(stringResource(R.string.add_carrier_supporting)) }
            )

            // === Límite de envíos alcanzado (free tier) ===
            if (uiState is AddUiState.LimitReached) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Límite de $FREE_SHIPMENT_LIMIT envíos activos",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Hazte Premium para envíos ilimitados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        TextButton(onClick = onUpgradeClick) {
                            Text(
                                "✦ Premium",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }

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
                        stringResource(R.string.add_save_button),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
