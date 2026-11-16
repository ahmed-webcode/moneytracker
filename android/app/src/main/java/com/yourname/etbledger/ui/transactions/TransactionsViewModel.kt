package com.yourname.etbledger.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.entity.AccountEntity
import com.yourname.etbledger.data.local.entity.TransactionEntity
import com.yourname.etbledger.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TransactionFilterType(val label: String) {
    ALL("All"),
    INCOME("Income"),
    OUTGOING("Outgoing"),
    SELF_TRANSFERS("Self-Transfers")
}

data class TransactionsUiState(
    val query: String = "",
    val filterType: TransactionFilterType = TransactionFilterType.ALL,
    val selectedAccountId: String? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList()
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filterType = MutableStateFlow(TransactionFilterType.ALL)
    private val selectedAccountId = MutableStateFlow<String?>(null)

    private val allTransactionsFlow = repository.observeTransactions(
        startMillis = 0L,
        endMillis = Long.MAX_VALUE,
        accountId = null
    )

    val uiState: StateFlow<TransactionsUiState> = combine(
        query,
        filterType,
        selectedAccountId,
        repository.observeAccounts(),
        allTransactionsFlow
    ) { q, typeFilter, accountId, accounts, allTx ->
        val trimmed = q.trim()
        val filtered = allTx.filter { tx ->
            val matchesAccount = accountId == null || tx.accountId == accountId
            val matchesType = when (typeFilter) {
                TransactionFilterType.ALL -> true
                TransactionFilterType.INCOME -> tx.type == TransactionType.INCOME && !tx.isSelfTransfer
                TransactionFilterType.OUTGOING -> tx.type == TransactionType.OUTGOING && !tx.isSelfTransfer
                TransactionFilterType.SELF_TRANSFERS -> tx.isSelfTransfer
            }
            val matchesQuery = trimmed.isEmpty() ||
                tx.counterparty?.contains(trimmed, ignoreCase = true) == true ||
                tx.category.contains(trimmed, ignoreCase = true) ||
                tx.referenceId?.contains(trimmed, ignoreCase = true) == true ||
                tx.note?.contains(trimmed, ignoreCase = true) == true
            matchesAccount && matchesType && matchesQuery
        }
        TransactionsUiState(
            query = q,
            filterType = typeFilter,
            selectedAccountId = accountId,
            accounts = accounts,
            transactions = filtered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState()
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilterType(type: TransactionFilterType) {
        filterType.value = type
    }

    fun setSelectedAccountId(accountId: String?) {
        selectedAccountId.value = accountId
    }

    fun addCashTransaction(
        type: TransactionType,
        amountMinor: Long,
        category: String,
        counterparty: String,
        note: String
    ) {
        viewModelScope.launch {
            repository.addManualCashTransaction(
                type = type,
                amountMinor = amountMinor,
                category = category,
                counterparty = counterparty,
                note = note
            )
        }
    }

    fun toggleSelfTransfer(txId: Long, isSelfTransfer: Boolean) {
        viewModelScope.launch { repository.toggleSelfTransfer(txId, isSelfTransfer) }
    }

    fun updateTransactionDetails(txId: Long, category: String, note: String?) {
        viewModelScope.launch { repository.updateTransactionDetails(txId, category, note) }
    }

    fun deleteTransaction(txId: Long) {
        viewModelScope.launch { repository.deleteTransaction(txId) }
    }
}
