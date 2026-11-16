package com.yourname.etbledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.etbledger.data.local.RuleMatchField
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.parser.MoneyUtils
import com.yourname.etbledger.ui.common.StandardCategories
import com.yourname.etbledger.ui.common.formatEtb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var budgetText by remember(state.settings.monthlyOutgoingBudgetMinor) {
        mutableStateOf(
            if (state.settings.monthlyOutgoingBudgetMinor > 0L) {
                (state.settings.monthlyOutgoingBudgetMinor / 100.0).toString()
            } else {
                ""
            }
        )
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings & Rules", fontWeight = FontWeight.Bold) })
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("SMS Sync & Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Real-time Notifications", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Alert when a new bank/wallet SMS arrives",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.settings.alertsEnabled,
                                onCheckedChange = viewModel::setAlertsEnabled
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = budgetText,
                                onValueChange = { budgetText = it },
                                label = { Text("Monthly Outgoing Alert (ETB)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                val minor = MoneyUtils.tryParseEtbToMinor(budgetText) ?: 0L
                                viewModel.setMonthlyBudgetMinor(minor)
                            }) {
                                Text("Save")
                            }
                        }

                        Text("Inbox Sync Window", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 90, 365).forEach { days ->
                                FilterChip(
                                    selected = state.settings.syncLookbackDays == days,
                                    onClick = { viewModel.setSyncLookbackDays(days) },
                                    label = { Text("${days}d") }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = viewModel::fullInboxRescan,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Run Full Inbox Rescan (All History)")
                        }
                    }
                }
            }

            if (state.accounts.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Tracked Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            state.accounts.forEach { account ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(account.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            formatEtb(account.currentBalanceMinor),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = account.isIncludedInTotal,
                                        onCheckedChange = { viewModel.toggleAccountIncluded(account.id, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Category Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = viewModel::reapplyRulesToUncategorized) {
                            Text("Re-apply")
                        }
                        Button(onClick = { showAddRuleDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Add")
                        }
                    }
                }
            }

            items(state.rules, key = { it.id }) { rule ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "\"${rule.keyword}\" → ${rule.category}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${rule.matchField.name} • ${rule.transactionTypeFilter?.name ?: "ALL"} • Priority ${rule.priority}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { viewModel.toggleRuleEnabled(rule.id, it) }
                        )
                        IconButton(onClick = { viewModel.deleteRule(rule.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Rule")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showAddRuleDialog) {
        AddCategoryRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onSave = { keyword, category, field, typeFilter, priority ->
                viewModel.addRule(keyword, category, field, typeFilter, priority)
                showAddRuleDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCategoryRuleDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, RuleMatchField, TransactionType?, Int) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food & Groceries") }
    var matchField by remember { mutableStateOf(RuleMatchField.ANY) }
    var typeFilter by remember { mutableStateOf<TransactionType?>(TransactionType.OUTGOING) }
    var priorityText by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category Rule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Keyword (e.g. Uber, Cafe, Kaldis)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Assign Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StandardCategories.filter { it != "Uncategorized" }.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Text("Match In", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleMatchField.entries.forEach { f ->
                        FilterChip(
                            selected = matchField == f,
                            onClick = { matchField = f },
                            label = { Text(f.name) }
                        )
                    }
                }

                Text("Transaction Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = { Text("Any") }
                    )
                    FilterChip(
                        selected = typeFilter == TransactionType.OUTGOING,
                        onClick = { typeFilter = TransactionType.OUTGOING },
                        label = { Text("Outgoing") }
                    )
                    FilterChip(
                        selected = typeFilter == TransactionType.INCOME,
                        onClick = { typeFilter = TransactionType.INCOME },
                        label = { Text("Income") }
                    )
                }

                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { priorityText = it },
                    label = { Text("Priority (higher runs first)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = keyword.isNotBlank(),
                onClick = {
                    val p = priorityText.toIntOrNull() ?: 10
                    onSave(keyword, category, matchField, typeFilter, p)
                }
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
