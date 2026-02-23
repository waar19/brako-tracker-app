package com.brk718.tracker.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.FileDownload
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
import com.brk718.tracker.BuildConfig
import com.brk718.tracker.R
import com.brk718.tracker.data.billing.BillingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onGmailClick: () -> Unit,
    onAmazonAuthClick: () -> Unit,
    onArchivedClick: () -> Unit,
    onStatsClick: () -> Unit = {},
    onExportCsvClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val prefs = state.preferences
    val context = LocalContext.current
    val activity = context as android.app.Activity
    val exportResult by viewModel.exportResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Reaccionar al resultado de exportación
    LaunchedEffect(exportResult) {
        when (val result = exportResult) {
            is ExportResult.Success -> {
                viewModel.clearExportResult()
                // Abrir share sheet para que el usuario guarde / comparta el CSV
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, result.uri)
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_csv_share_subject))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Guardar o compartir CSV"))
            }
            is ExportResult.Error -> {
                viewModel.clearExportResult()
                // LaunchedEffect ya es una corrutina — llamamos showSnackbar directamente
                snackbarHostState.showSnackbar("Error al exportar: ${result.message}")
            }
            else -> Unit
        }
    }

    // Launcher para solicitar permiso POST_NOTIFICATIONS (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // El usuario respondió; si fue concedido, activar la preferencia
        viewModel.setNotificationsEnabled(granted)
    }

    // Dialogs
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    var showQuietHoursStartDialog by remember { mutableStateOf(false) }
    var showQuietHoursEndDialog by remember { mutableStateOf(false) }
    var showDisconnectAmazonDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showPremiumSyncDialog by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    val isPremium = prefs.isPremium

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // ── PREMIUM ──────────────────────────────────────────────
            item {
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_premium),
                    icon = Icons.Default.WorkspacePremium
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (prefs.isPremium)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (prefs.isPremium) {
                            // Estado premium activo
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.settings_premium_active_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.settings_premium_active_benefits),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.restorePurchases() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_premium_restore))
                            }
                        } else {
                            // Oferta premium
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB400),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.settings_premium_cta_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.settings_premium_free_benefits),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            // Feedback de error si aplica
                            if (state.billingState is BillingState.Error) {
                                Text(
                                    (state.billingState as BillingState.Error).message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Button(
                                onClick = { viewModel.purchaseSubscription(activity) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.billingState !is BillingState.Loading
                            ) {
                                if (state.billingState is BillingState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    val price = state.subscriptionPriceText
                                    Text(
                                        if (price == "—") stringResource(R.string.settings_premium_subscribe_no_price)
                                        else stringResource(R.string.settings_premium_subscribe_price, price)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
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
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── NOTIFICACIONES ───────────────────────────────────────
            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_notifications), icon = Icons.Default.Notifications)
            }
            item {
                SettingsCardGroup {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_notifications_title),
                        subtitle = stringResource(R.string.settings_notifications_subtitle),
                        icon = Icons.Default.NotificationsActive,
                        checked = prefs.notificationsEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setNotificationsEnabled(enable)
                            }
                        }
                    )
                    SettingsItemDivider()
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_important_only_title),
                        subtitle = stringResource(R.string.settings_important_only_subtitle),
                        icon = Icons.Default.FilterList,
                        checked = prefs.onlyImportantEvents,
                        enabled = prefs.notificationsEnabled,
                        onCheckedChange = { viewModel.setOnlyImportantEvents(it) }
                    )
                    SettingsItemDivider()
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_quiet_hours_title),
                        subtitle = stringResource(R.string.settings_quiet_hours_subtitle),
                        icon = Icons.Default.BedtimeOff,
                        checked = prefs.quietHoursEnabled,
                        enabled = prefs.notificationsEnabled,
                        onCheckedChange = { viewModel.setQuietHoursEnabled(it) }
                    )
                    if (prefs.quietHoursEnabled && prefs.notificationsEnabled) {
                        SettingsItemDivider()
                        SettingsNavigationItem(
                            title = stringResource(R.string.settings_quiet_hours_start_title),
                            subtitle = "%02d:00".format(prefs.quietHoursStart),
                            icon = Icons.Default.NightsStay,
                            onClick = { showQuietHoursStartDialog = true }
                        )
                        SettingsItemDivider()
                        SettingsNavigationItem(
                            title = stringResource(R.string.settings_quiet_hours_end_title),
                            subtitle = "%02d:00".format(prefs.quietHoursEnd),
                            icon = Icons.Default.WbSunny,
                            onClick = { showQuietHoursEndDialog = true }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── SINCRONIZACIÓN ───────────────────────────────────────
            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_sync), icon = Icons.Default.Sync)
            }
            item {
                SettingsCardGroup {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_autosync_title),
                        subtitle = stringResource(R.string.settings_autosync_subtitle),
                        icon = Icons.Default.Autorenew,
                        checked = prefs.autoSync,
                        onCheckedChange = { viewModel.setAutoSync(it) }
                    )
                    SettingsItemDivider()
                    val intervalLabel = when (prefs.syncIntervalHours) {
                        -1   -> stringResource(R.string.settings_sync_30min_premium)
                        0    -> stringResource(R.string.settings_sync_manual)
                        1    -> stringResource(R.string.settings_sync_1h)
                        2    -> stringResource(R.string.settings_sync_2h)
                        6    -> stringResource(R.string.settings_sync_6h)
                        12   -> stringResource(R.string.settings_sync_12h)
                        else -> stringResource(R.string.settings_sync_nh, prefs.syncIntervalHours)
                    }
                    SettingsNavigationItem(
                        title = stringResource(R.string.settings_sync_interval_title),
                        subtitle = intervalLabel,
                        icon = Icons.Default.Schedule,
                        enabled = prefs.autoSync,
                        onClick = { showSyncIntervalDialog = true }
                    )
                    SettingsItemDivider()
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_sync_wifi_title),
                        subtitle = stringResource(R.string.settings_sync_wifi_subtitle),
                        icon = Icons.Default.Wifi,
                        checked = prefs.syncOnlyOnWifi,
                        enabled = prefs.autoSync,
                        onCheckedChange = { viewModel.setSyncOnlyOnWifi(it) }
                    )
                    SettingsItemDivider()
                    val syncFailed = state.lastSyncText == "Falló la última sincronización"
                    SettingsCustomTrailingItem(
                        title = stringResource(R.string.settings_sync_status_title),
                        subtitle = state.lastSyncText,
                        icon = if (syncFailed) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        trailingContent = {
                            if (syncFailed) {
                                TextButton(onClick = { viewModel.syncNow() }) {
                                    Text(stringResource(R.string.settings_sync_retry))
                                }
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── ENVÍOS ───────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_shipments), icon = Icons.Default.Inventory2)
            }
            item {
                SettingsCardGroup {
                    SettingsNavigationItem(
                        title = stringResource(R.string.settings_archived_title),
                        subtitle = stringResource(R.string.settings_archived_subtitle),
                        icon = Icons.Default.Archive,
                        onClick = onArchivedClick
                    )
                    SettingsItemDivider()
                    SettingsNavigationItem(
                        title = stringResource(R.string.settings_csv_export_title),
                        subtitle = when {
                            !isPremium                           -> "✦ Solo Premium"
                            exportResult is ExportResult.Loading -> "Exportando..."
                            else                                 -> stringResource(R.string.settings_csv_export_subtitle)
                        },
                        icon = Icons.Default.FileDownload,
                        enabled = isPremium && exportResult !is ExportResult.Loading,
                        onClick = {
                            if (isPremium) viewModel.exportCsv()
                            else showPremiumSyncDialog = true
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── INTEGRACIONES ────────────────────────────────────────
            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_integrations), icon = Icons.Default.Link)
            }
            item {
                val amazonSubtitle = if (state.isAmazonConnected)
                    stringResource(R.string.settings_amazon_session_active)
                else
                    stringResource(R.string.settings_amazon_not_connected)
                val amazonTrailing: @Composable () -> Unit = {
                    if (state.isAmazonConnected) {
                        TextButton(
                            onClick = { showDisconnectAmazonDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text(stringResource(R.string.settings_amazon_disconnect)) }
                    } else {
                        TextButton(onClick = onAmazonAuthClick) { Text(stringResource(R.string.settings_amazon_connect)) }
                    }
                }
                SettingsCardGroup {
                    SettingsCustomTrailingItem(
                        title = stringResource(R.string.settings_amazon_section_title),
                        subtitle = amazonSubtitle,
                        icon = Icons.Default.ShoppingCart,
                        trailingContent = amazonTrailing
                    )
                    SettingsItemDivider()
                    SettingsNavigationItem(
                        title = stringResource(R.string.settings_gmail_title),
                        subtitle = stringResource(R.string.settings_gmail_subtitle),
                        icon = Icons.Default.Email,
                        onClick = onGmailClick
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── APARIENCIA ───────────────────────────────────────────
            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance), icon = Icons.Default.Palette)
            }
            item {
                SettingsCardGroup {
                    val themeLabel = when (prefs.theme) {
                        "light"  -> stringResource(R.string.settings_theme_light)
                        "dark"   -> stringResource(R.string.settings_theme_dark)
                        else     -> stringResource(R.string.settings_theme_system)
                    }
                    SettingsNavigationItem(
                        title = stringResource(R.string.settings_theme_title),
                        subtitle = themeLabel,
                        icon = Icons.Default.DarkMode,
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── ESTADÍSTICAS ─────────────────────────────────────────
            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_stats), icon = Icons.Default.BarChart)
            }
            item {
                SettingsCardGroup {
                    SettingsNavigationItem(
                        title = stringResource(R.string.settings_stats_title),
                        subtitle = stringResource(R.string.settings_stats_subtitle),
                        icon = Icons.Default.BarChart,
                        onClick = onStatsClick
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── ACERCA DE ────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_about), icon = Icons.Default.Info)
            }
            item {
                SettingsCardGroup {
                    SettingsInfoItem(
                        title = stringResource(R.string.settings_version_title),
                        subtitle = state.appVersion,
                        icon = Icons.Default.Tag
                    )
                    SettingsItemDivider()
                    SettingsNavigationItem(
                        title = stringResource(R.string.settings_clear_map_cache_title),
                        subtitle = if (cacheCleared)
                            stringResource(R.string.settings_map_cache_cleared)
                        else
                            stringResource(R.string.settings_clear_map_cache_subtitle),
                        icon = Icons.Default.DeleteSweep,
                        onClick = { showClearCacheDialog = true }
                    )
                }
            }

            // ── DEVELOPER (solo en builds DEBUG) ─────────────────────
            if (BuildConfig.DEBUG) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    SettingsSectionHeader(
                        title = "Developer",
                        icon = Icons.Default.BugReport
                    )
                }
                item {
                    SettingsCardGroup {
                        SettingsSwitchItem(
                            title = "Simular Premium",
                            subtitle = if (prefs.isPremium) "Modo Premium activo" else "Modo Free activo",
                            icon = Icons.Default.AdminPanelSettings,
                            checked = prefs.isPremium,
                            onCheckedChange = { viewModel.setIsPremiumDebug(it) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ──── Dialogs ────

    if (showThemeDialog) {
        ThemePickerDialog(
            current = prefs.theme,
            onSelect = { viewModel.setTheme(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showQuietHoursStartDialog) {
        HourPickerDialog(
            title = stringResource(R.string.settings_quiet_hours_start_title),
            current = prefs.quietHoursStart,
            onSelect = { viewModel.setQuietHoursStart(it); showQuietHoursStartDialog = false },
            onDismiss = { showQuietHoursStartDialog = false }
        )
    }

    if (showQuietHoursEndDialog) {
        HourPickerDialog(
            title = stringResource(R.string.settings_quiet_hours_end_title),
            current = prefs.quietHoursEnd,
            onSelect = { viewModel.setQuietHoursEnd(it); showQuietHoursEndDialog = false },
            onDismiss = { showQuietHoursEndDialog = false }
        )
    }

    if (showSyncIntervalDialog) {
        SyncIntervalDialog(
            current       = prefs.syncIntervalHours,
            isPremium     = isPremium,
            onSelect      = { viewModel.setSyncIntervalHours(it); showSyncIntervalDialog = false },
            onUpgradeClick = { showPremiumSyncDialog = true },
            onDismiss     = { showSyncIntervalDialog = false }
        )
    }

    if (showDisconnectAmazonDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectAmazonDialog = false },
            icon = { Icon(Icons.Default.LinkOff, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_disconnect_amazon_title)) },
            text = { Text(stringResource(R.string.dialog_disconnect_amazon_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnectAmazon()
                        showDisconnectAmazonDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_amazon_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectAmazonDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, null) },
            title = { Text(stringResource(R.string.dialog_clear_cache_title)) },
            text = { Text(stringResource(R.string.dialog_clear_cache_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearMapCache()
                    cacheCleared = true
                    showClearCacheDialog = false
                }) { Text(stringResource(R.string.dialog_clear_cache_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    // Dialog: función exclusiva premium
    if (showPremiumSyncDialog) {
        AlertDialog(
            onDismissRequest = { showPremiumSyncDialog = false },
            icon = {
                Icon(
                    Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color(0xFFFFB400),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text(stringResource(R.string.settings_premium_feature_title)) },
            text = { Text(stringResource(R.string.settings_premium_feature_message)) },
            confirmButton = {
                Button(onClick = {
                    showPremiumSyncDialog = false
                    viewModel.purchaseSubscription(activity)
                }) { Text(stringResource(R.string.settings_premium_feature_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumSyncDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

// ──────────────────────────────────────────────────────────
// Composables reutilizables de Settings
// ──────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Card contenedor para un grupo de items de settings. */
@Composable
private fun SettingsCardGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column { content() }
    }
}

/** Divisor fino entre items dentro de un SettingsCardGroup. */
@Composable
private fun SettingsItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun SettingsNavigationItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsInfoItem(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsCustomTrailingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailingContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailingContent()
    }
}

// ──────────────────────────────────────────────────────────
// Dialogs
// ──────────────────────────────────────────────────────────

@Composable
private fun ThemePickerDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "system" to stringResource(R.string.settings_theme_system),
        "light"  to stringResource(R.string.settings_theme_light),
        "dark"   to stringResource(R.string.settings_theme_dark)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DarkMode, null) },
        title = { Text(stringResource(R.string.settings_theme_title)) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = current == value,
                            onClick = { onSelect(value) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun SyncIntervalDialog(
    current: Int,
    isPremium: Boolean,
    onSelect: (Int) -> Unit,
    onUpgradeClick: () -> Unit,
    onDismiss: () -> Unit
) {
    // 30 min = valor -1 (usamos -1 internamente para "30 min", WorkManager acepta 15 min como mínimo)
    data class SyncOption(val value: Int, val label: String, val premiumOnly: Boolean = false)
    val options = listOf(
        SyncOption(-1, "Cada 30 min", premiumOnly = true),
        SyncOption(1,  stringResource(R.string.settings_sync_1h)),
        SyncOption(2,  stringResource(R.string.settings_sync_2h)),
        SyncOption(6,  stringResource(R.string.settings_sync_6h)),
        SyncOption(12, stringResource(R.string.settings_sync_12h)),
        SyncOption(0,  stringResource(R.string.settings_sync_manual))
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Schedule, null) },
        title = { Text(stringResource(R.string.settings_sync_interval_title)) },
        text = {
            Column {
                options.forEach { opt ->
                    val enabled = !opt.premiumOnly || isPremium
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (enabled) onSelect(opt.value)
                                else { onDismiss(); onUpgradeClick() }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = current == opt.value,
                            onClick = {
                                if (enabled) onSelect(opt.value)
                                else { onDismiss(); onUpgradeClick() }
                            },
                            enabled = enabled
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            opt.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outline
                        )
                        if (opt.premiumOnly && !isPremium) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFB400).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "✦ Premium",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB8860B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun HourPickerDialog(
    title: String,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hours = (0..23).toList()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Schedule, null) },
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(240.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(hours) { hour ->
                    val selected = hour == current
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onSelect(hour) }
                    ) {
                        Text(
                            text = "%02d:00".format(hour),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

