package com.yourname.etbledger.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.dao.IncomeOutgoingSummary
import com.yourname.etbledger.data.local.entity.AccountEntity
import com.yourname.etbledger.data.local.entity.TransactionEntity
import com.yourname.etbledger.data.repository.LedgerRepository
import com.yourname.etbledger.data.repository.SyncSummary
import com.yourname.etbledger.ui.common.TimeframePreset
import com.yourname.etbledger.ui.common.TimeframeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val totalBalanceMinor: Long = 0L,
    val accounts: List<AccountEntity> = emptyList(),
    val timeframe: TimeframeRange = TimeframeRange.forPreset(TimeframePreset.THIS_MONTH),
    val summary: IncomeOutgoingSummary = IncomeOutgoingSummary(0L, 0L, 0L),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val isSyncing: Boolean = false,
    val lastSyncMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    private val timeframe = MutableStateFlow(TimeframeRange.forPreset(TimeframePreset.THIS_MONTH))
    private val isSyncing = MutableStateFlow(false)
    private val syncMessage = MutableStateFlow<String?>(null)

    private val summaryFlow = timeframe.flatMapLatest { range ->
        repository.observeSummary(range.startMillis, range.endMillis)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeTotalBalanceMinor(),
        repository.observeAccounts(),
        timeframe,
        summaryFlow,
        repository.observeRecentTransactions(15),
        isSyncing,
        syncMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DashboardUiState(
            totalBalanceMinor = values[0] as Long,
            accounts = values[1] as List<AccountEntity>,
            timeframe = values[2] as TimeframeRange,
            summary = values[3] as IncomeOutgoingSummary,
            recentTransactions = values[4] as List<TransactionEntity>,
            isSyncing = values[5] as Boolean,
            lastSyncMessage = values[6] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun selectTimeframe(range: TimeframeRange) {
        timeframe.value = range
    }

    fun syncNow(silentIfEmpty: Boolean = false) {
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            val result: SyncSummary = repository.syncInbox()
            isSyncing.value = false
            if (result.permissionMissing) {
                if (!silentIfEmpty) {
                    syncMessage.value = "Grant SMS permissions to sync bank messages"
                }
            } else if (!silentIfEmpty || result.newlyParsedCount > 0 || result.newlyUnparsedCount > 0) {
                syncMessage.value =
                    "Synced: ${result.newlyParsedCount} new, ${result.newlyUnparsedCount} to review"
            }
        }
    }

    fun clearSyncMessage() {
        syncMessage.value = null
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
