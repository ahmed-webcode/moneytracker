package com.yourname.etbledger.ui.reports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.etbledger.data.export.CsvExporter
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.dao.CategoryBreakdownRow
import com.yourname.etbledger.data.local.dao.IncomeOutgoingSummary
import com.yourname.etbledger.data.local.entity.AccountEntity
import com.yourname.etbledger.data.local.entity.TransactionEntity
import com.yourname.etbledger.data.repository.LedgerRepository
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class ChartGrouping(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month")
}

data class CashFlowBucket(
    val label: String,
    val incomeMinor: Long,
    val outgoingMinor: Long
)

data class ReportsUiState(
    val timeframe: TimeframeRange = TimeframeRange.forPreset(TimeframePreset.THIS_MONTH),
    val selectedAccountId: String? = null,
    val grouping: ChartGrouping = ChartGrouping.DAY,
    val accounts: List<AccountEntity> = emptyList(),
    val summary: IncomeOutgoingSummary = IncomeOutgoingSummary(0L, 0L, 0L),
    val chartBuckets: List<CashFlowBucket> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdownRow> = emptyList(),
    val exportMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val csvExporter: CsvExporter
) : ViewModel() {

    private val timeframe = MutableStateFlow(TimeframeRange.forPreset(TimeframePreset.THIS_MONTH))
    private val selectedAccountId = MutableStateFlow<String?>(null)
    private val grouping = MutableStateFlow(ChartGrouping.DAY)
    private val exportMessage = MutableStateFlow<String?>(null)

    private val filterParams = combine(timeframe, selectedAccountId) { tf, acct -> tf to acct }

    private val summaryFlow = filterParams.flatMapLatest { (tf, acct) ->
        repository.observeSummary(tf.startMillis, tf.endMillis, acct)
    }

    private val transactionsFlow = filterParams.flatMapLatest { (tf, acct) ->
        repository.observeTransactions(tf.startMillis, tf.endMillis, acct)
    }

    private val breakdownFlow = filterParams.flatMapLatest { (tf, acct) ->
        repository.observeCategoryBreakdown(tf.startMillis, tf.endMillis, acct)
    }

    val uiState: StateFlow<ReportsUiState> = combine(
        filterParams,
        grouping,
        repository.observeAccounts(),
        summaryFlow,
        transactionsFlow,
        breakdownFlow,
        exportMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val (tf, acctId) = values[0] as Pair<TimeframeRange, String?>
        val grp = values[1] as ChartGrouping
        val accounts = values[2] as List<AccountEntity>
        val summary = values[3] as IncomeOutgoingSummary
        val txs = values[4] as List<TransactionEntity>
        val breakdown = values[5] as List<CategoryBreakdownRow>
        val msg = values[6] as String?

        ReportsUiState(
            timeframe = tf,
            selectedAccountId = acctId,
            grouping = grp,
            accounts = accounts,
            summary = summary,
            chartBuckets = aggregateBuckets(txs, grp),
            categoryBreakdown = breakdown,
            exportMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsUiState()
    )

    fun selectTimeframe(range: TimeframeRange) {
        timeframe.value = range
    }

    fun selectAccount(accountId: String?) {
        selectedAccountId.value = accountId
    }

    fun selectGrouping(group: ChartGrouping) {
        grouping.value = group
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val tf = timeframe.value
            val acct = selectedAccountId.value
            val txs = repository.getTransactionsSnapshot(tf.startMillis, tf.endMillis, acct)
            val accountsMap = uiState.value.accounts.associateBy { it.id }
            val result = csvExporter.exportTransactionsToUri(uri, txs, accountsMap)
            exportMessage.value = result.fold(
                onSuccess = { count -> "Exported $count transactions to CSV" },
                onFailure = { err -> "Export failed: ${err.message}" }
            )
        }
    }

    fun clearExportMessage() {
        exportMessage.value = null
    }

    private fun aggregateBuckets(
        transactions: List<TransactionEntity>,
        grouping: ChartGrouping
    ): List<CashFlowBucket> {
        val zone = ZoneId.systemDefault()
        val nonSelf = transactions.filter { !it.isSelfTransfer }
        if (nonSelf.isEmpty()) return emptyList()

        val dayFmt = DateTimeFormatter.ofPattern("MM/dd")
        val monthFmt = DateTimeFormatter.ofPattern("MMM yy")

        return when (grouping) {
            ChartGrouping.DAY -> {
                nonSelf.groupBy {
                    Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
                }.toSortedMap().map { (date, list) ->
                    toBucket(dayFmt.format(date), list)
                }
            }
            ChartGrouping.WEEK -> {
                nonSelf.groupBy {
                    val date = Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                }.toSortedMap().map { (weekStart, list) ->
                    toBucket("W ${dayFmt.format(weekStart)}", list)
                }
            }
            ChartGrouping.MONTH -> {
                nonSelf.groupBy {
                    val date = Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
                    YearMonth.from(date)
                }.toSortedMap().map { (ym, list) ->
                    toBucket(monthFmt.format(ym.atDay(1)), list)
                }
            }
        }
    }

    private fun toBucket(label: String, list: List<TransactionEntity>): CashFlowBucket {
        val income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
        val outgoing = list.filter { it.type == TransactionType.OUTGOING }.sumOf { it.totalAmountMinor }
        return CashFlowBucket(label = label, incomeMinor = income, outgoingMinor = outgoing)
    }
}
