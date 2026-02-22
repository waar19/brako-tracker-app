package com.brk718.tracker.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.ShipmentWithEvents
import com.brk718.tracker.data.local.UserPreferencesRepository
import com.brk718.tracker.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ──── Constants ────

private const val FREE_ARCHIVE_DAYS = 30L  // días de historial visibles en tier gratuito

// ──── UiState ────

data class ArchivedUiState(
    val visibleShipments: List<ShipmentWithEvents> = emptyList(),
    val hiddenCount: Int = 0,   // cuántos están ocultos por ser muy viejos (solo free)
    val isPremium: Boolean = false
)

// ──── ViewModel ────

@HiltViewModel
class ArchivedViewModel @Inject constructor(
    private val repository: ShipmentRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<ArchivedUiState> = combine(
        repository.archivedShipments,
        prefsRepo.preferences
    ) { all, prefs ->
        if (prefs.isPremium) {
            ArchivedUiState(visibleShipments = all, hiddenCount = 0, isPremium = true)
        } else {
            val cutoff  = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(FREE_ARCHIVE_DAYS)
            val visible = all.filter { it.shipment.lastUpdate >= cutoff }
            val hidden  = all.size - visible.size
            ArchivedUiState(visibleShipments = visible, hiddenCount = hidden, isPremium = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchivedUiState())

    fun unarchive(id: String) = viewModelScope.launch { repository.unarchiveShipment(id) }
    fun delete(id: String)    = viewModelScope.launch { repository.deleteShipment(id) }

    // ── Selección múltiple ────────────────────────────────────────────────────
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleSelection(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun selectAll() {
        _selectedIds.value = uiState.value.visibleShipments.map { it.shipment.id }.toSet()
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun unarchiveSelected() {
        viewModelScope.launch {
            _selectedIds.value.forEach { repository.unarchiveShipment(it) }
            _selectedIds.value = emptySet()
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            _selectedIds.value.forEach { repository.deleteShipment(it) }
            _selectedIds.value = emptySet()
        }
    }
}

// ──── Screen ────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit = {},
    viewModel: ArchivedViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // ── Barra contextual de selección ─────────────────────────────
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
                        // Desarchivar seleccionados
                        IconButton(onClick = { viewModel.unarchiveSelected() }) {
                            Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.archived_selection_unarchive))
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
                // ── Barra normal ──────────────────────────────────────────────
                TopAppBar(
                    title = { Text(stringResource(R.string.archived_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.archived_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        if (state.visibleShipments.isEmpty() && state.hiddenCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.archived_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(state.visibleShipments, key = { it.shipment.id }) { item ->
                    val isSelected = selectedIds.contains(item.shipment.id)
                    val visibleState = remember {
                        MutableTransitionState(false).apply { targetState = true }
                    }
                    AnimatedVisibility(
                        visibleState = visibleState,
                        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
                        modifier = Modifier.animateItem()
                    ) {
                        if (isSelectionMode) {
                            // En modo selección: tap = toggle, sin swipe
                            SelectableArchivedCard(
                                item = item,
                                isSelected = isSelected,
                                onToggle = { viewModel.toggleSelection(item.shipment.id) }
                            )
                        } else {
                            // Modo normal: swipe derecha = desarchivar, izquierda = eliminar
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            viewModel.unarchive(item.shipment.id)
                                            true
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            viewModel.delete(item.shipment.id)
                                            true
                                        }
                                        else -> false
                                    }
                                },
                                positionalThreshold = { it * 0.4f }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = { ArchivedSwipeBackground(dismissState) }
                            ) {
                                ArchivedShipmentCard(
                                    item = item,
                                    onLongClick = { viewModel.toggleSelection(item.shipment.id) }
                                )
                            }
                        }
                    }
                }

                // Banner de upgrade al final si hay archivados ocultos (free)
                if (!state.isPremium && state.hiddenCount > 0) {
                    item {
                        ArchivedPremiumBanner(
                            hiddenCount    = state.hiddenCount,
                            onUpgradeClick = onUpgradeClick
                        )
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

// ──── Swipe background ────

@Composable
private fun ArchivedSwipeBackground(dismissState: SwipeToDismissBoxState) {
    val isStartToEnd = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
    val isEndToStart = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

    val color = when {
        isStartToEnd -> MaterialTheme.colorScheme.primaryContainer   // desarchivar → azul
        isEndToStart -> MaterialTheme.colorScheme.errorContainer      // eliminar → rojo
        else         -> Color.Transparent
    }
    val icon = when {
        isStartToEnd -> Icons.Default.Unarchive
        isEndToStart -> Icons.Default.Delete
        else         -> null
    }
    val iconTint = when {
        isStartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
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

// ──── Banner premium ────

@Composable
private fun ArchivedPremiumBanner(
    hiddenCount: Int,
    onUpgradeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = Color(0xFFFFB400),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$hiddenCount envío${if (hiddenCount > 1) "s" else ""} oculto${if (hiddenCount > 1) "s" else ""}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "El plan gratuito muestra solo los últimos $FREE_ARCHIVE_DAYS días. Hazte Premium para ver todo el historial.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onUpgradeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ver historial completo — Premium")
            }
        }
    }
}

// ──── Card normal (modo swipe) ────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivedShipmentCard(
    item: ShipmentWithEvents,
    onLongClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.shipment.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${ShipmentRepository.displayName(item.shipment.carrier)} • ${item.shipment.trackingNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(item.shipment.lastUpdate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            // Hint visual: iconos semitransparentes que indican las acciones de swipe
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Unarchive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ──── Card selectable (modo selección múltiple) ────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableArchivedCard(
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
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
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
