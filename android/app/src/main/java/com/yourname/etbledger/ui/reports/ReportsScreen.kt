package com.yourname.etbledger.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.etbledger.data.local.dao.CategoryBreakdownRow
import com.yourname.etbledger.ui.common.TimeframeSelectorRow
import com.yourname.etbledger.ui.common.formatEtb
import com.yourname.etbledger.ui.theme.ChartPalette
import com.yourname.etbledger.ui.theme.IncomeGreen
import com.yourname.etbledger.ui.theme.OutgoingCoral
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsv(uri)
        }
    }

    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & Analytics", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            csvLauncher.launch("etb_ledger_report_${System.currentTimeMillis() / 1000}.csv")
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export CSV")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TimeframeSelectorRow(
                    selected = state.timeframe,
                    onSelect = viewModel::selectTimeframe
                )
            }

            if (state.accounts.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.selectedAccountId == null,
                                onClick = { viewModel.selectAccount(null) },
                                label = { Text("All Accounts") }
                            )
                        }
                        items(state.accounts, key = { it.id }) { acct ->
                            FilterChip(
                                selected = state.selectedAccountId == acct.id,
                                onClick = { viewModel.selectAccount(acct.id) },
                                label = { Text(acct.displayName) }
                            )
                        }
                    }
                }
            }

            item {
                IncomeVsOutgoingCard(
                    buckets = state.chartBuckets,
                    grouping = state.grouping,
                    onSelectGrouping = viewModel::selectGrouping,
                    totalIncomeMinor = state.summary.totalIncomeMinor,
                    totalOutgoingMinor = state.summary.totalOutgoingMinor
                )
            }

            item {
                CategoryBreakdownCard(rows = state.categoryBreakdown)
            }
        }
    }
}

@Composable
private fun IncomeVsOutgoingCard(
    buckets: List<CashFlowBucket>,
    grouping: ChartGrouping,
    onSelectGrouping: (ChartGrouping) -> Unit,
    totalIncomeMinor: Long,
    totalOutgoingMinor: Long
) {
    var selectedIdx by remember(buckets) { mutableIntStateOf(-1) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Income vs. Outgoing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(color = IncomeGreen, label = "In: ${formatEtb(totalIncomeMinor)}")
                LegendDot(color = OutgoingCoral, label = "Out: ${formatEtb(totalOutgoingMinor)}")
            }

            Spacer(modifier = Modifier.height(10.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ChartGrouping.entries.forEachIndexed { index, grp ->
                    SegmentedButton(
                        selected = grouping == grp,
                        onClick = { onSelectGrouping(grp) },
                        shape = SegmentedButtonDefaults.itemShape(index, ChartGrouping.entries.size)
                    ) {
                        Text(grp.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (buckets.isEmpty()) {
                Text(
                    text = "No transactions in this range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val visibleBuckets = buckets.takeLast(8)
                val maxMinor = visibleBuckets.maxOf { max(it.incomeMinor, it.outgoingMinor) }
                    .coerceAtLeast(1L)

                if (selectedIdx in visibleBuckets.indices) {
                    val b = visibleBuckets[selectedIdx]
                    Text(
                        text = "${b.label}: In ${formatEtb(b.incomeMinor)} • Out ${formatEtb(b.outgoingMinor)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val count = visibleBuckets.size
                    val slotWidth = size.width / count
                    val barWidth = (slotWidth * 0.28f).coerceAtMost(28.dp.toPx())
                    val gap = 4.dp.toPx()

                    for (step in 1..3) {
                        val y = size.height * (1f - step / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    visibleBuckets.forEachIndexed { i, bucket ->
                        val centerX = slotWidth * i + slotWidth / 2f
                        val inH = (bucket.incomeMinor.toFloat() / maxMinor) * (size.height - 8.dp.toPx())
                        val outH = (bucket.outgoingMinor.toFloat() / maxMinor) * (size.height - 8.dp.toPx())

                        if (inH > 0f) {
                            drawRoundRect(
                                color = IncomeGreen,
                                topLeft = Offset(centerX - barWidth - gap / 2f, size.height - inH),
                                size = Size(barWidth, inH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                        if (outH > 0f) {
                            drawRoundRect(
                                color = OutgoingCoral,
                                topLeft = Offset(centerX + gap / 2f, size.height - outH),
                                size = Size(barWidth, outH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    visibleBuckets.forEachIndexed { idx, bucket ->
                        Text(
                            text = bucket.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedIdx == idx) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedIdx = idx }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(rows: List<CategoryBreakdownRow>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Outgoing by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            val total = rows.sumOf { it.totalMinor }
            if (rows.isEmpty() || total <= 0L) {
                Text(
                    text = "No outgoing expenses in this range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(160.dp)) {
                        val stroke = 24.dp.toPx()
                        var startAngle = -90f
                        rows.forEachIndexed { index, row ->
                            val sweep = (row.totalMinor.toFloat() / total.toFloat()) * 360f
                            val color = ChartPalette[index % ChartPalette.size]
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Out",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatEtb(total),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEachIndexed { index, row ->
                        val color = ChartPalette[index % ChartPalette.size]
                        val pct = (row.totalMinor * 100.0) / total
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = row.category,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${formatEtb(row.totalMinor)} (${"%.1f".format(pct)}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
