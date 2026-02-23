package com.brk718.tracker.ui.home

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.R
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.repository.ShipmentRepository
import com.brk718.tracker.ui.add.FREE_SHIPMENT_LIMIT
import com.brk718.tracker.ui.ads.AdManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onShipmentClick: (String) -> Unit,
    onGmailClick: () -> Unit = {},
    onAmazonAuthClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    isPremium: Boolean = false,
    adManager: AdManager? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val shipments by viewModel.shipments.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val shouldShowRating by viewModel.shouldShowRatingRequest.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val canAddMore = isPremium || shipments.size < FREE_SHIPMENT_LIMIT

    // Diálogo de confirmación para eliminar seleccionados
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // BackHandler: salir del modo selección al pulsar atrás
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    // Rating: lanzar In-App Review cuando el usuario tenga >= 3 entregas exitosas
    val activity = LocalContext.current as android.app.Activity
    LaunchedEffect(shouldShowRating) {
        if (shouldShowRating) {
            val reviewManager = ReviewManagerFactory.create(activity)
            reviewManager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    reviewManager.launchReviewFlow(activity, task.result)
                }
                // Si falla, no pasa nada — Google Play lo gestiona silenciosamente
            }
        }
    }

    // Estado de la barra de búsqueda
    var searchVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // No llamar refreshAll() en ON_RESUME — la lista se actualiza reactivamente
    // desde Room (Flow) y WorkManager sincroniza en background.
    // refreshAll() solo se llama cuando el usuario toca el botón de actualizar.

    // Al cerrar búsqueda limpiar query
    LaunchedEffect(searchVisible) {
        if (!searchVisible) {
            viewModel.clearSearch()
        } else {
            // Pequeño delay para que el campo esté renderizado antes de pedir foco
            delay(100)
            focusRequester.requestFocus()
        }
    }

    // Mostrar snackbar cuando falla el refresh
    LaunchedEffect(refreshError) {
        refreshError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearRefreshError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                if (isSelectionMode) {
                    // ── Barra contextual de selección ─────────────────────
                    TopAppBar(
                        title = {
                            Text(
                                if (selectedIds.size == 1)
                                    stringResource(R.string.home_selected_count, selectedIds.size)
                                else
                                    stringResource(R.string.home_selected_count_plural, selectedIds.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_selection_cancel))
                            }
                        },
                        actions = {
                            // Seleccionar todos
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.home_selection_select_all))
                            }
                            // Archivar seleccionados
                            IconButton(onClick = { viewModel.archiveSelected() }) {
                                Icon(Icons.Default.Archive, contentDescription = stringResource(R.string.home_selection_archive))
                            }
                            // Eliminar seleccionados (con confirmación)
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.home_selection_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                } else {
                    // ── Barra normal ──────────────────────────────────────
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.home_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        navigationIcon = {
                            // Al activar búsqueda mostramos "X" en la izquierda
                            if (searchVisible) {
                                IconButton(onClick = {
                                    searchVisible = false
                                    keyboardController?.hide()
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cerrar búsqueda"
                                    )
                                }
                            }
                        },
                        actions = {
                            if (!isPremium) {
                                IconButton(onClick = onUpgradeClick) {
                                    Icon(
                                        Icons.Default.WorkspacePremium,
                                        contentDescription = "Premium",
                                        tint = Color(0xFFFFB400)
                                    )
                                }
                            }
                            // Ícono de búsqueda — toggle
                            IconButton(onClick = { searchVisible = !searchVisible }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Buscar",
                                    tint = if (searchVisible) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { viewModel.refreshAll() },
                                enabled = !isRefreshing
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.home_refresh_all))
                            }
                            IconButton(onClick = onGmailClick) {
                                Icon(Icons.Default.Email, contentDescription = stringResource(R.string.home_import_gmail))
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings))
                            }
                        }
                    )
                }

                // Banner sin conexión
                AnimatedVisibility(
                    visible = !isOnline,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                stringResource(R.string.home_offline_banner),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Chip de límite de envíos — aparece cuando el usuario free alcanza ≥60% del límite
                val shipmentLimitThreshold = (FREE_SHIPMENT_LIMIT * 0.6f).toInt()
                AnimatedVisibility(
                    visible = !isPremium && !isSelectionMode && shipments.size >= shipmentLimitThreshold,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpgradeClick() },
                        color = if (shipments.size >= FREE_SHIPMENT_LIMIT)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = if (shipments.size >= FREE_SHIPMENT_LIMIT)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    stringResource(R.string.home_limit_chip_count, shipments.size, FREE_SHIPMENT_LIMIT),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (shipments.size >= FREE_SHIPMENT_LIMIT)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Text(
                                stringResource(R.string.home_limit_chip_upgrade),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (shipments.size >= FREE_SHIPMENT_LIMIT)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Barra de búsqueda colapsable
                AnimatedVisibility(
                    visible = searchVisible,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_search_clear),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (canAddMore) {
                        onAddClick()
                    } else {
                        onUpgradeClick()
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_new_shipment)) },
                containerColor = if (canAddMore)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        },
        bottomBar = {
            if (!isPremium && adManager != null) {
                AndroidView(
                    factory = { adManager.createBannerAdView() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAll() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (shipments.isEmpty() && !isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (searchQuery.isNotEmpty()) {
                        // Estado vacío para búsqueda sin resultados
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.home_search_no_results, searchQuery),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Empty state real con CTAs
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.home_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.home_empty_subtitle_extended),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(28.dp))
                            Button(
                                onClick = onAddClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.home_new_shipment))
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onGmailClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.home_import_gmail))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shipments, key = { it.shipment.id }) { item ->
                        val isSelected = selectedIds.contains(item.shipment.id)
                        // M2: Animación de entrada al aparecer la card
                        val visibleState = remember {
                            MutableTransitionState(false).apply { targetState = true }
                        }
                        AnimatedVisibility(
                            visibleState = visibleState,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
                            modifier = Modifier.animateItem()
                        ) {
                            if (isSelectionMode) {
                                // En modo selección: tap = toggle, no swipe
                                SelectableShipmentCard(
                                    item = item,
                                    isSelected = isSelected,
                                    onToggle = { viewModel.toggleSelection(item.shipment.id) }
                                )
                            } else {
                                // Modo normal: swipe + long-press para entrar en selección
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        when (value) {
                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                viewModel.archiveShipment(item.shipment.id)
                                                true
                                            }
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                viewModel.deleteShipment(item.shipment.id)
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                                    positionalThreshold = { it * 0.4f }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = { SwipeBackground(dismissState) }
                                ) {
                                    ShipmentCard(
                                        item = item,
                                        onClick = { onShipmentClick(item.shipment.id) },
                                        onLongClick = { viewModel.toggleSelection(item.shipment.id) },
                                        onArchive = { viewModel.archiveShipment(item.shipment.id) },
                                        onDelete = { viewModel.deleteShipment(item.shipment.id) },
                                        onAmazonAuthClick = onAmazonAuthClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    // ── Diálogo confirmar eliminación en bloque ───────────────────────────────
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(
                    if (selectedIds.size == 1)
                        stringResource(R.string.home_delete_confirm_title, selectedIds.size)
                    else
                        stringResource(R.string.home_delete_confirm_title_plural, selectedIds.size)
                )
            },
            text = { Text(stringResource(R.string.home_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.home_delete_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.home_cancel))
                }
            }
        )
    }
    }
}

/** Fondo que se muestra detrás de la card al hacer swipe */
@Composable
private fun SwipeBackground(dismissState: SwipeToDismissBoxState) {
    // targetValue refleja la dirección del swipe en progreso
    val isStartToEnd = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
    val isEndToStart = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

    val color = when {
        isStartToEnd -> MaterialTheme.colorScheme.secondaryContainer   // archivar → verde/azul
        isEndToStart -> MaterialTheme.colorScheme.errorContainer        // eliminar → rojo
        else         -> Color.Transparent
    }
    val icon = when {
        isStartToEnd -> Icons.Default.Archive
        isEndToStart -> Icons.Default.Delete
        else         -> null
    }
    val iconTint = when {
        isStartToEnd -> MaterialTheme.colorScheme.onSecondaryContainer
        isEndToStart -> MaterialTheme.colorScheme.onErrorContainer
        else         -> Color.Transparent
    }
    val alignment = when {
        isStartToEnd -> Alignment.CenterStart
        else         -> Alignment.CenterEnd
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShipmentCard(
    item: ShipmentWithEvents,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAmazonAuthClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val status = item.shipment.status
    val statusLower = status.lowercase()

    val (statusColor, statusContainerColor) = when {
        statusLower == "entregado" ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
        statusLower.contains("error") || statusLower.contains("incidencia") ||
        statusLower.contains("novedad") || statusLower.contains("intento") ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        statusLower.contains("manual") || statusLower.contains("no soportado") ||
        statusLower.contains("seguimiento manual") ->
            MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.surfaceVariant
        statusLower.contains("pendiente") || statusLower.contains("pre-envío") ||
        statusLower.contains("pre envío") || statusLower.contains("registrando") ->
            Color(0xFFF57F17) to Color(0xFFFFF9C4)   // amarillo — aún no enviado
        statusLower.contains("tránsito") || statusLower.contains("transito") ||
        statusLower.contains("reparto") || statusLower.contains("camino") ->
            MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
        statusLower.contains("devuelto") ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        else ->
            MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
    }

    // Inicial del transportista para el avatar (usando nombre de display)
    val carrierDisplayName = ShipmentRepository.displayName(item.shipment.carrier)
    val carrierInitial = carrierDisplayName.firstOrNull()?.uppercaseChar() ?: '?'
    val carrierAvatarColor = when (item.shipment.carrier.lowercase()) {
        "amazon"                   -> Color(0xFFFF9900)
        "ups"                      -> Color(0xFF351C15)
        "fedex"                    -> Color(0xFF4D148C)
        "usps"                     -> Color(0xFF004B87)
        "dhl"                      -> Color(0xFFFFCC00)
        "interrapidisimo-scraper"  -> Color(0xFFE30613)   // Rojo corporativo Inter
        "coordinadora"             -> Color(0xFF003087)
        "servientrega"             -> Color(0xFF009B48)
        "envia-co"                 -> Color(0xFFFF6B00)
        else                       -> MaterialTheme.colorScheme.secondaryContainer
    }
    val carrierTextColor = when (item.shipment.carrier.lowercase()) {
        "dhl" -> Color(0xFF333333)
        else  -> Color.White
    }

    // Último evento
    val lastEvent = item.events.maxByOrNull { it.timestamp }
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    val needsAmazonLogin = status == "LOGIN_REQUIRED" || status == "Sign-In required" ||
        (item.shipment.trackingNumber.startsWith("111-") && status == "No disponible")

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila superior: avatar + info + menú
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar transportista
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(carrierAvatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = carrierInitial.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = carrierTextColor
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Título + tracking
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.shipment.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.shipment.trackingNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Menú de acciones
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.home_menu_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        // ── Compartir ──────────────────────────────────────────
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_share)) },
                            leadingIcon = {
                                Icon(Icons.Default.Share, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                menuExpanded = false
                                val dateStr = lastEvent?.let {
                                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                        .format(Date(it.timestamp))
                                } ?: "—"
                                val shareText = buildString {
                                    appendLine("📦 ${item.shipment.title}")
                                    appendLine("Transportista: ${ShipmentRepository.displayName(item.shipment.carrier)}")
                                    appendLine("Número: ${item.shipment.trackingNumber}")
                                    appendLine("Estado: ${item.shipment.status}")
                                    append("Última actualización: $dateStr")
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.home_share_chooser))
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_menu_archive)) },
                            leadingIcon = {
                                Icon(Icons.Default.Archive, null,
                                    tint = MaterialTheme.colorScheme.secondary)
                            },
                            onClick = { menuExpanded = false; onArchive() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_menu_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null,
                                    tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Fila inferior: chip de estado + último evento
            if (needsAmazonLogin) {
                Button(
                    onClick = onAmazonAuthClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.home_reconnect_amazon))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chip de estado con fondo
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusContainerColor,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Último evento (tiempo relativo)
                    if (lastEvent != null) {
                        Text(
                            text = relativeTime(lastEvent.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Último evento descripción
                if (lastEvent != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append(lastEvent.description)
                            if (!lastEvent.location.isNullOrBlank()) {
                                append(" • ")
                                append(lastEvent.location)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Fecha estimada de entrega (solo si existe y el envío no fue entregado)
                val eta = item.shipment.estimatedDelivery
                if (eta != null && statusLower != "entregado") {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Llega ~${SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(eta))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** Card con checkbox de selección para el modo bulk */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableShipmentCard(
    item: ShipmentWithEvents,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Checkbox visual
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Seleccionado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            CircleShape
                        )
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.shipment.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${ShipmentRepository.displayName(item.shipment.carrier)} • ${item.shipment.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Convierte un timestamp a texto relativo: "hace 5 min", "hace 2 h", "hace 3 d" */
private fun relativeTime(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1   -> "ahora"
        minutes < 60  -> "hace $minutes min"
        hours   < 24  -> "hace $hours h"
        days    < 30  -> "hace $days d"
        else          -> { val m = days / 30; "hace $m ${if (m == 1L) "mes" else "meses"}" }
    }
}
