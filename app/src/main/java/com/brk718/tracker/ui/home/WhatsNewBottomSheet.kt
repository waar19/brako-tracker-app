package com.brk718.tracker.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brk718.tracker.R

private val Gold = Color(0xFFFFB400)

/**
 * BottomSheet "¿Qué hay de nuevo?" que se muestra una sola vez
 * tras cada actualización de la app.
 *
 * Para añadir entradas en versiones futuras, basta con añadir
 * nuevas strings y un nuevo Triple a la lista [entries].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewBottomSheet(
    versionName: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Cabecera ──────────────────────────────────────────
            Icon(
                Icons.Default.NewReleases,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.whats_new_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                versionName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            // ── Entradas de novedades ─────────────────────────────
            // Para versiones futuras: añadir nuevas entradas aquí
            // y las strings correspondientes en strings.xml
            val entries = listOf(
                Triple(Icons.Default.BarChart,     stringResource(R.string.whats_new_v2_stats_title),    stringResource(R.string.whats_new_v2_stats_sub)),
                Triple(Icons.Default.Archive,      stringResource(R.string.whats_new_v2_archived_title), stringResource(R.string.whats_new_v2_archived_sub)),
                Triple(Icons.Default.BedtimeOff,   stringResource(R.string.whats_new_v2_quiet_title),    stringResource(R.string.whats_new_v2_quiet_sub)),
                Triple(Icons.Default.FileDownload, stringResource(R.string.whats_new_v2_csv_title),      stringResource(R.string.whats_new_v2_csv_sub)),
                Triple(Icons.Default.Search,       stringResource(R.string.whats_new_v2_search_title),   stringResource(R.string.whats_new_v2_search_sub)),
                Triple(Icons.Default.Widgets,      stringResource(R.string.whats_new_v2_widget_title),   stringResource(R.string.whats_new_v2_widget_sub))
            )

            entries.forEach { (icon, title, subtitle) ->
                WhatsNewEntry(icon = icon, title = title, subtitle = subtitle)
            }

            Spacer(Modifier.height(28.dp))

            // ── Botón de cierre ───────────────────────────────────
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(R.string.whats_new_dismiss),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun WhatsNewEntry(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
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
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
