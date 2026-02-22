package com.brk718.tracker.ui.paywall

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.R
import com.brk718.tracker.data.billing.BillingState
import com.brk718.tracker.ui.settings.SettingsViewModel

private val Gold = Color(0xFFFFB400)
private val GoldDark = Color(0xFFB8860B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isPremium = state.preferences.isPremium
    val billingState = state.billingState
    val price = state.subscriptionPriceText
    val activity = LocalContext.current as Activity

    // Auto-cerrar si el usuario compra premium desde esta pantalla
    LaunchedEffect(isPremium) {
        if (isPremium) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ícono premium
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Gold.copy(alpha = 0.15f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            // Badge de prueba gratuita
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    stringResource(R.string.paywall_trial_badge),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            // Beneficios
            val benefits = listOf(
                Triple(Icons.Default.Block, stringResource(R.string.paywall_benefit_no_ads_title), stringResource(R.string.paywall_benefit_no_ads_subtitle)),
                Triple(Icons.Default.AllInclusive, stringResource(R.string.paywall_benefit_unlimited_title), stringResource(R.string.paywall_benefit_unlimited_subtitle)),
                Triple(Icons.Default.History, stringResource(R.string.paywall_benefit_history_title), stringResource(R.string.paywall_benefit_history_subtitle)),
                Triple(Icons.Default.Bolt, stringResource(R.string.paywall_benefit_sync_title), stringResource(R.string.paywall_benefit_sync_subtitle)),
                Triple(Icons.Default.FileDownload, stringResource(R.string.paywall_benefit_csv_title), stringResource(R.string.paywall_benefit_csv_subtitle)),
                Triple(Icons.Default.Widgets, stringResource(R.string.paywall_benefit_widget_title), stringResource(R.string.paywall_benefit_widget_subtitle))
            )

            benefits.forEach { (icon, title, subtitle) ->
                BenefitRow(icon = icon, title = title, subtitle = subtitle)
            }

            Spacer(Modifier.height(32.dp))

            // Precio y botón de compra
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (price != "—") {
                        Text(
                            price,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            stringResource(R.string.paywall_per_month_hint, formatPerMonth(price)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    if (billingState is BillingState.Error) {
                        Text(
                            billingState.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Button(
                        onClick = { viewModel.purchaseSubscription(activity) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = billingState !is BillingState.Loading,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (billingState is BillingState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.WorkspacePremium, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (price == "—") stringResource(R.string.paywall_cta_no_price)
                                else stringResource(R.string.paywall_cta_price, price),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { viewModel.restorePurchases() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.settings_premium_restore_alt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.paywall_terms),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Calcula precio mensual aproximado a partir del string de precio anual, ej: "$12.99" → "$1.08" */
private fun formatPerMonth(annualPrice: String): String {
    // Extraer dígitos y punto decimal
    val digits = annualPrice.filter { it.isDigit() || it == '.' }
    val annual = digits.toDoubleOrNull() ?: return "—"
    val monthly = annual / 12.0
    val prefix = annualPrice.firstOrNull { !it.isDigit() && it != '.' } ?: '$'
    return "$prefix${"%.2f".format(monthly)}"
}
