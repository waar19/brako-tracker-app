package com.brk718.tracker.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onGmailClick: () -> Unit,
    onAmazonAuthClick: () -> Unit,
    onArchivedClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val prefs = state.preferences

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
    var showDisconnectAmazonDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // ──────────────────────────────────────────────
            // NOTIFICACIONES
            // ──────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Notificaciones", icon = Icons.Default.Notifications)
            }
            item {
                SettingsSwitchItem(
                    title = "Notificaciones de seguimiento",
                    subtitle = "Recibir alertas de cambios de estado",
                    icon = Icons.Default.NotificationsActive,
                    checked = prefs.notificationsEnabled,
                    onCheckedChange = { enable ->
                        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Android 13+ requiere solicitar el permiso explícitamente
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsEnabled(enable)
                        }
                    }
                )
            }
            item {
                SettingsSwitchItem(
                    title = "Solo eventos importantes",
                    subtitle = "Entregado, En camino, Problema",
                    icon = Icons.Default.FilterList,
                    checked = prefs.onlyImportantEvents,
                    enabled = prefs.notificationsEnabled,
                    onCheckedChange = { viewModel.setOnlyImportantEvents(it) }
                )
            }

            item { SettingsDivider() }

            // ──────────────────────────────────────────────
            // SINCRONIZACIÓN
            // ──────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Sincronización", icon = Icons.Default.Sync)
            }
            item {
                SettingsSwitchItem(
                    title = "Sincronización automática",
                    subtitle = "Actualiza los envíos en segundo plano",
                    icon = Icons.Default.Autorenew,
                    checked = prefs.autoSync,
                    onCheckedChange = { viewModel.setAutoSync(it) }
                )
            }
            item {
                val intervalLabel = when (prefs.syncIntervalHours) {
                    0    -> "Solo manual"
                    1    -> "Cada hora"
                    2    -> "Cada 2 horas"
                    6    -> "Cada 6 horas"
                    12   -> "Cada 12 horas"
                    else -> "Cada ${prefs.syncIntervalHours} horas"
                }
                SettingsNavigationItem(
                    title = "Frecuencia de sincronización",
                    subtitle = intervalLabel,
                    icon = Icons.Default.Schedule,
                    enabled = prefs.autoSync,
                    onClick = { showSyncIntervalDialog = true }
                )
            }
            item {
                SettingsSwitchItem(
                    title = "Solo con WiFi",
                    subtitle = "No sincronizar usando datos móviles",
                    icon = Icons.Default.Wifi,
                    checked = prefs.syncOnlyOnWifi,
                    enabled = prefs.autoSync,
                    onCheckedChange = { viewModel.setSyncOnlyOnWifi(it) }
                )
            }
            item {
                SettingsInfoItem(
                    title = "Estado de sincronización",
                    subtitle = state.lastSyncText,
                    icon = Icons.Default.CloudDone
                )
            }

            item { SettingsDivider() }

            // ──────────────────────────────────────────────
            // ENVÍOS
            // ──────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Envíos", icon = Icons.Default.Inventory2)
            }
            item {
                SettingsNavigationItem(
                    title = "Envíos archivados",
                    subtitle = "Ver y gestionar envíos archivados",
                    icon = Icons.Default.Archive,
                    onClick = onArchivedClick
                )
            }

            item { SettingsDivider() }

            // ──────────────────────────────────────────────
            // INTEGRACIONES
            // ──────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Integraciones", icon = Icons.Default.Link)
            }
            item {
                val amazonSubtitle = if (state.isAmazonConnected) "Sesión activa" else "No conectado"
                val amazonTrailing: @Composable () -> Unit = {
                    if (state.isAmazonConnected) {
                        TextButton(
                            onClick = { showDisconnectAmazonDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Desconectar") }
                    } else {
                        TextButton(onClick = onAmazonAuthClick) { Text("Conectar") }
                    }
                }
                SettingsCustomTrailingItem(
                    title = "Amazon",
                    subtitle = amazonSubtitle,
                    icon = Icons.Default.ShoppingCart,
                    trailingContent = amazonTrailing
                )
            }
            item {
                SettingsNavigationItem(
                    title = "Gmail",
                    subtitle = "Importar envíos desde correos de Gmail",
                    icon = Icons.Default.Email,
                    onClick = onGmailClick
                )
            }

            item { SettingsDivider() }

            // ──────────────────────────────────────────────
            // APARIENCIA
            // ──────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Apariencia", icon = Icons.Default.Palette)
            }
            item {
                val themeLabel = when (prefs.theme) {
                    "light"  -> "Claro"
                    "dark"   -> "Oscuro"
                    else     -> "Seguir el sistema"
                }
                SettingsNavigationItem(
                    title = "Tema",
                    subtitle = themeLabel,
                    icon = Icons.Default.DarkMode,
                    onClick = { showThemeDialog = true }
                )
            }

            item { SettingsDivider() }

            // ──────────────────────────────────────────────
            // ACERCA DE
            // ──────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Acerca de", icon = Icons.Default.Info)
            }
            item {
                SettingsInfoItem(
                    title = "Versión",
                    subtitle = state.appVersion,
                    icon = Icons.Default.Tag
                )
            }
            item {
                SettingsNavigationItem(
                    title = "Limpiar caché de mapas",
                    subtitle = if (cacheCleared) "¡Caché eliminada!" else "Liberar espacio de tiles descargados",
                    icon = Icons.Default.DeleteSweep,
                    onClick = { showClearCacheDialog = true }
                )
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

    if (showSyncIntervalDialog) {
        SyncIntervalDialog(
            current = prefs.syncIntervalHours,
            onSelect = { viewModel.setSyncIntervalHours(it); showSyncIntervalDialog = false },
            onDismiss = { showSyncIntervalDialog = false }
        )
    }

    if (showDisconnectAmazonDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectAmazonDialog = false },
            icon = { Icon(Icons.Default.LinkOff, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Desconectar Amazon") },
            text = { Text("Se eliminará la sesión guardada. Tendrás que iniciar sesión de nuevo para rastrear pedidos de Amazon.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnectAmazon()
                        showDisconnectAmazonDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Desconectar") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectAmazonDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, null) },
            title = { Text("Limpiar caché de mapas") },
            text = { Text("Se eliminarán todos los tiles de mapa descargados. Se volverán a descargar cuando uses el mapa.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearMapCache()
                    cacheCleared = true
                    showClearCacheDialog = false
                }) { Text("Limpiar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancelar") }
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

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
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
}

@Composable
fun SettingsNavigationItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
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
}

@Composable
fun SettingsInfoItem(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
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
}

@Composable
fun SettingsCustomTrailingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailingContent: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
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
    val options = listOf("system" to "Seguir el sistema", "light" to "Claro", "dark" to "Oscuro")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DarkMode, null) },
        title = { Text("Tema") },
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
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun SyncIntervalDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(1 to "Cada hora", 2 to "Cada 2 horas", 6 to "Cada 6 horas", 12 to "Cada 12 horas", 0 to "Solo manual")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Schedule, null) },
        title = { Text("Frecuencia de sincronización") },
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
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
