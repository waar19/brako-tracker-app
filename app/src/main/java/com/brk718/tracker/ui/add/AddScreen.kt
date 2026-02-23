package com.brk718.tracker.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
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
import com.brk718.tracker.util.CarrierDetector

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
    "castores"           -> Color(0xFF2D6A4F) // verde oscuro Castores
    "avianca cargo"      -> Color(0xFFD62828) // rojo Avianca
    "tcc"                -> Color(0xFF1565C0) // azul TCC
    "saferbo"            -> Color(0xFF4A148C) // morado Saferbo
    "deprisa"            -> Color(0xFFE65100) // naranja Deprisa
    "picap"              -> Color(0xFF00897B) // verde azulado Picap
    "mensajeros urbanos" -> Color(0xFF37474F) // gris oscuro Mensajeros Urbanos
    else                 -> Color(0xFF535D7E)
}

private fun carrierTextColor(carrier: String): Color = when (carrier.lowercase()) {
    "dhl" -> Color(0xFF1A1A1A)
    else  -> Color.White
}

@Composable
private fun carrierHint(carrier: String): String = when (carrier.lowercase()) {
    "amazon"             -> stringResource(R.string.add_hint_amazon)
    "ups"                -> stringResource(R.string.add_hint_ups)
    "fedex"              -> stringResource(R.string.add_hint_fedex)
    "usps"               -> stringResource(R.string.add_hint_usps)
    "dhl"                -> stringResource(R.string.add_hint_dhl)
    "interrapidísimo"    -> stringResource(R.string.add_hint_interrapidisimo)
    "coordinadora"       -> stringResource(R.string.add_hint_coordinadora)
    "servientrega"       -> stringResource(R.string.add_hint_servientrega)
    "envía"              -> stringResource(R.string.add_hint_envia)
    "listo"              -> stringResource(R.string.add_hint_listo)
    "treda"              -> stringResource(R.string.add_hint_treda)
    "speed"              -> stringResource(R.string.add_hint_speed)
    "castores"           -> stringResource(R.string.add_hint_castores)
    "avianca cargo"      -> stringResource(R.string.add_hint_avianca_cargo)
    "tcc"                -> stringResource(R.string.add_hint_tcc)
    "saferbo"            -> stringResource(R.string.add_hint_saferbo)
    "deprisa"            -> stringResource(R.string.add_hint_deprisa)
    "picap"              -> stringResource(R.string.add_hint_picap)
    "mensajeros urbanos" -> stringResource(R.string.add_hint_mensajeros_urbanos)
    else                 -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onUpgradeClick: () -> Unit = onBack,
    onScanClick: () -> Unit = {},
    scannedBarcode: String? = null,
    viewModel: AddShipmentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var trackingNumber by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var carrier by remember { mutableStateOf("") }

    // Cuando llegue un valor escaneado desde la pantalla de scanner, aplicarlo al campo
    LaunchedEffect(scannedBarcode) {
        if (!scannedBarcode.isNullOrBlank()) {
            trackingNumber = scannedBarcode
        }
    }

    // Detección automática del transportista al escribir
    val detectedCarrier by remember(trackingNumber) {
        derivedStateOf { CarrierDetector.detect(trackingNumber) }
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
                },
                actions = {
                    IconButton(onClick = onScanClick) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = stringResource(R.string.add_scan_barcode),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
