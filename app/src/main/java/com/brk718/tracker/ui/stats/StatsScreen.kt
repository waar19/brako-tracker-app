package com.brk718.tracker.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis estadísticas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Tarjetas KPI ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    modifier = Modifier.weight(1f),
                    value = state.totalShipments.toString(),
                    label = "Rastreados",
                    icon = Icons.Default.Inventory2,
                    color = MaterialTheme.colorScheme.primary
                )
                KpiCard(
                    modifier = Modifier.weight(1f),
                    value = state.deliveredShipments.toString(),
                    label = "Entregados",
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF22C55E)
                )
                KpiCard(
                    modifier = Modifier.weight(1f),
                    value = "${state.successRate}%",
                    label = "Éxito",
                    icon = Icons.Default.TrendingUp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // ── Segunda fila KPI ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    modifier = Modifier.weight(1f),
                    value = state.activeShipments.toString(),
                    label = "Activos",
                    icon = Icons.Default.LocalShipping,
                    color = Color(0xFF3B82F6)
                )
                KpiCard(
                    modifier = Modifier.weight(1f),
                    value = if (state.avgDeliveryDays > 0f)
                        "${"%.1f".format(state.avgDeliveryDays)}d"
                    else "—",
                    label = "Días promedio",
                    icon = Icons.Default.Schedule,
                    color = MaterialTheme.colorScheme.secondary
                )
                KpiCard(
                    modifier = Modifier.weight(1f),
                    value = if (state.topCarrierCount > 0) state.topCarrierCount.toString() else "—",
                    label = if (state.topCarrier.isNotEmpty())
                        state.topCarrier.take(10)
                    else "Transportista",
                    icon = Icons.Default.Star,
                    color = Color(0xFFFFB400)
                )
            }

            // ── Gráfica de barras: envíos por mes ────────────────────────
            if (state.monthBars.isNotEmpty()) {
                StatsCard(title = "Envíos por mes", icon = Icons.Default.BarChart) {
                    BarChart(
                        bars = state.monthBars,
                        barColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(top = 8.dp)
                    )
                }
            }

            // ── Gráfica de dona: estado de envíos activos ────────────────
            if (state.statusSlices.isNotEmpty()) {
                StatsCard(title = "Estado de envíos activos", icon = Icons.Default.PieChart) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        DonutChart(
                            slices = state.statusSlices,
                            modifier = Modifier.size(130.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.statusSlices.forEach { slice ->
                                LegendRow(
                                    color = Color(slice.colorHex),
                                    label = slice.label,
                                    count = slice.count,
                                    total = state.activeShipments
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Composables internos ──────────────────────────────────────────────────────

@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

// ── Gráfica de barras ─────────────────────────────────────────────────────────

@Composable
private fun BarChart(
    bars: List<MonthBar>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val maxCount = bars.maxOfOrNull { it.count } ?: 1
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(bars) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val countColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val barCount = bars.size
            if (barCount == 0) return@Canvas

            val totalWidth  = size.width
            val totalHeight = size.height
            val barWidth    = (totalWidth / barCount) * 0.55f
            val gapWidth    = (totalWidth - barWidth * barCount) / (barCount + 1)
            val labelHeight = 36.dp.toPx()
            val chartHeight = totalHeight - labelHeight

            bars.forEachIndexed { i, bar ->
                val x = gapWidth * (i + 1) + barWidth * i
                val barH = if (maxCount > 0)
                    (bar.count.toFloat() / maxCount) * chartHeight * animProgress.value
                else 0f
                val top  = chartHeight - barH
                val cornerPx = 6.dp.toPx()

                // Barra con esquinas superiores redondeadas
                if (barH > cornerPx * 2) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, top),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(cornerPx)
                    )
                } else if (barH > 0) {
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, top),
                        size = Size(barWidth, barH)
                    )
                }

                // Número encima de la barra
                if (bar.count > 0 && animProgress.value > 0.8f) {
                    drawContext.canvas.nativeCanvas.drawText(
                        bar.count.toString(),
                        x + barWidth / 2,
                        (top - 4.dp.toPx()).coerceAtLeast(4.dp.toPx()),
                        android.graphics.Paint().apply {
                            color = countColor.copy(alpha = 1f)
                                .let { c ->
                                    android.graphics.Color.argb(
                                        (c.alpha * 255).toInt(),
                                        (c.red   * 255).toInt(),
                                        (c.green * 255).toInt(),
                                        (c.blue  * 255).toInt()
                                    )
                                }
                            textSize = 10.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                            isAntiAlias = true
                        }
                    )
                }
            }
        }

        // Etiquetas de mes debajo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Gráfica de dona ───────────────────────────────────────────────────────────

@Composable
private fun DonutChart(
    slices: List<StatusSlice>,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.count }.toFloat()
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(slices) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        val radius      = (size.minDimension - strokeWidth) / 2f
        val center      = Offset(size.width / 2f, size.height / 2f)
        val startX      = center.x - radius
        val startY      = center.y - radius
        val diameter    = radius * 2f

        var startAngle = -90f

        slices.forEach { slice ->
            val sweep = (slice.count / total) * 360f * animProgress.value
            drawArc(
                color      = Color(slice.colorHex),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter  = false,
                topLeft    = Offset(startX, startY),
                size       = Size(diameter, diameter),
                style      = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }

        // Hueco central
        drawCircle(
            color  = surfaceColor,
            radius = radius - strokeWidth / 2f,
            center = center
        )
    }
}

// ── Fila de leyenda ───────────────────────────────────────────────────────────

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    count: Int,
    total: Int
) {
    val pct = if (total > 0) (count * 100 / total) else 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count ($pct%)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}
