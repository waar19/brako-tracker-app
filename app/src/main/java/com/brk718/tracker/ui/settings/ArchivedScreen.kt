package com.brk718.tracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    Scaffold(
        topBar = {
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
                    ArchivedShipmentCard(
                        item        = item,
                        onUnarchive = { viewModel.unarchive(item.shipment.id) },
                        onDelete    = { viewModel.delete(item.shipment.id) }
                    )
                }

                // Banner de upgrade al final si hay archivados ocultos por ser old (free)
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

// ──── Card ────

@Composable
private fun ArchivedShipmentCard(
    item: ShipmentWithEvents,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
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
                    text = "${item.shipment.carrier} • ${item.shipment.trackingNumber}",
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
            IconButton(onClick = onUnarchive) {
                Icon(
                    Icons.Default.Unarchive,
                    contentDescription = stringResource(R.string.archived_unarchive),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.archived_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
