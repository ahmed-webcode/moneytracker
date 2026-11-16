package com.yourname.etbledger.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

enum class TimeframePreset(val label: String) {
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    CUSTOM("Custom")
}

data class TimeframeRange(
    val preset: TimeframePreset,
    val startMillis: Long,
    val endMillis: Long
) {
    val displayLabel: String
        get() = when (preset) {
            TimeframePreset.THIS_WEEK -> "This Week"
            TimeframePreset.THIS_MONTH -> "This Month"
            TimeframePreset.CUSTOM -> "${formatShortDate(startMillis)} – ${formatShortDate(endMillis)}"
        }

    companion object {
        fun forPreset(preset: TimeframePreset): TimeframeRange {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            return when (preset) {
                TimeframePreset.THIS_WEEK -> {
                    val startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val endDate = startDate.plusDays(6)
                    TimeframeRange(
                        preset = TimeframePreset.THIS_WEEK,
                        startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                        endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                    )
                }
                TimeframePreset.THIS_MONTH, TimeframePreset.CUSTOM -> {
                    val startDate = today.withDayOfMonth(1)
                    val endDate = today.withDayOfMonth(today.lengthOfMonth())
                    TimeframeRange(
                        preset = TimeframePreset.THIS_MONTH,
                        startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                        endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeframeSelectorRow(
    selected: TimeframeRange,
    onSelect: (TimeframeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected.preset == TimeframePreset.THIS_WEEK,
            onClick = { onSelect(TimeframeRange.forPreset(TimeframePreset.THIS_WEEK)) },
            label = { Text("This Week") }
        )
        FilterChip(
            selected = selected.preset == TimeframePreset.THIS_MONTH,
            onClick = { onSelect(TimeframeRange.forPreset(TimeframePreset.THIS_MONTH)) },
            label = { Text("This Month") }
        )
        FilterChip(
            selected = selected.preset == TimeframePreset.CUSTOM,
            onClick = { showCustomPicker = true },
            label = {
                Text(
                    if (selected.preset == TimeframePreset.CUSTOM) selected.displayLabel else "Custom"
                )
            }
        )
    }

    if (showCustomPicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = selected.startMillis,
            initialSelectedEndDateMillis = selected.endMillis
        )
        DatePickerDialog(
            onDismissRequest = { showCustomPicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                    onClick = {
                        val s = state.selectedStartDateMillis ?: return@TextButton
                        val e = state.selectedEndDateMillis ?: return@TextButton
                        val zone = ZoneId.systemDefault()
                        val startLocal = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
                        val endLocal = Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate()
                        onSelect(
                            TimeframeRange(
                                preset = TimeframePreset.CUSTOM,
                                startMillis = startLocal.atStartOfDay(zone).toInstant().toEpochMilli(),
                                endMillis = endLocal.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                            )
                        )
                        showCustomPicker = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = state)
        }
    }
}
