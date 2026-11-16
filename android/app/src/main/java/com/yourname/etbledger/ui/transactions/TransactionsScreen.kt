package com.yourname.etbledger.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.etbledger.data.local.entity.TransactionEntity
import com.yourname.etbledger.ui.common.AddCashTransactionDialog
import com.yourname.etbledger.ui.common.TransactionDetailBottomSheet
import com.yourname.etbledger.ui.common.TransactionRowCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accountsMap = remember(state.accounts) { state.accounts.associateBy { it.id } }

    var showAddCashDialog by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddCashDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Cash")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search name, category, ref ID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TransactionFilterType.entries) { filter ->
                    FilterChip(
                        selected = state.filterType == filter,
                        onClick = { viewModel.setFilterType(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            if (state.accounts.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedAccountId == null,
                            onClick = { viewModel.setSelectedAccountId(null) },
                            label = { Text("All Accounts") }
                        )
                    }
                    items(state.accounts, key = { it.id }) { acct ->
                        FilterChip(
                            selected = state.selectedAccountId == acct.id,
                            onClick = { viewModel.setSelectedAccountId(acct.id) },
                            label = { Text(acct.displayName) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.transactions.isEmpty()) {
                    item {
                        Text(
                            text = "No matching transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.transactions, key = { it.id }) { tx ->
                        TransactionRowCard(
                            transaction = tx,
                            accountDisplayName = accountsMap[tx.accountId]?.displayName ?: tx.accountId,
                            onClick = { selectedTransaction = tx }
                        )
                    }
                }
            }
        }
    }

    if (showAddCashDialog) {
        AddCashTransactionDialog(
            onDismiss = { showAddCashDialog = false },
            onConfirm = viewModel::addCashTransaction
        )
    }

    selectedTransaction?.let { tx ->
        TransactionDetailBottomSheet(
            transaction = tx,
            account = accountsMap[tx.accountId],
            onDismiss = { selectedTransaction = null },
            onToggleSelfTransfer = { viewModel.toggleSelfTransfer(tx.id, it) },
            onSaveCategoryAndNote = { cat, note ->
                viewModel.updateTransactionDetails(tx.id, cat, note)
            },
            onDelete = {
                viewModel.deleteTransaction(tx.id)
                selectedTransaction = null
            }
        )
    }
}
