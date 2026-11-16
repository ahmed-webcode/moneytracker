package com.yourname.etbledger.data.repository

import com.yourname.etbledger.data.local.AccountType
import com.yourname.etbledger.data.local.RuleMatchField
import com.yourname.etbledger.data.local.TransactionSource
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.UnparsedStatus
import com.yourname.etbledger.data.local.dao.AccountDao
import com.yourname.etbledger.data.local.dao.CategoryBreakdownRow
import com.yourname.etbledger.data.local.dao.CategoryRuleDao
import com.yourname.etbledger.data.local.dao.IncomeOutgoingSummary
import com.yourname.etbledger.data.local.dao.TransactionDao
import com.yourname.etbledger.data.local.dao.UnparsedSmsDao
import com.yourname.etbledger.data.local.entity.AccountEntity
import com.yourname.etbledger.data.local.entity.CategoryRuleEntity
import com.yourname.etbledger.data.local.entity.TransactionEntity
import com.yourname.etbledger.data.local.entity.UnparsedSmsEntity
import com.yourname.etbledger.data.notification.TransactionNotificationHelper
import com.yourname.etbledger.data.parser.BankSmsParserEngine
import com.yourname.etbledger.data.preferences.AppPreferences
import com.yourname.etbledger.data.sms.SmsInboxReader
import com.yourname.etbledger.domain.model.ParseOutcome
import com.yourname.etbledger.domain.model.ParsedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SyncSummary(
    val scannedCount: Int,
    val newlyParsedCount: Int,
    val duplicateCount: Int,
    val newlyUnparsedCount: Int,
    val permissionMissing: Boolean = false
)

@Singleton
class LedgerRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val categoryRuleDao: CategoryRuleDao,
    private val unparsedSmsDao: UnparsedSmsDao,
    private val parserEngine: BankSmsParserEngine,
    private val smsInboxReader: SmsInboxReader,
    private val notificationHelper: TransactionNotificationHelper,
    private val preferences: AppPreferences
) {
    private val syncMutex = Mutex()

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAllAccounts()

    fun observeTotalBalanceMinor(): Flow<Long> = accountDao.observeTotalBalanceMinor()

    fun observeRecentTransactions(limit: Int = 15): Flow<List<TransactionEntity>> =
        transactionDao.observeRecentTransactions(limit)

    fun observeTransactions(
        startMillis: Long,
        endMillis: Long,
        accountId: String? = null
    ): Flow<List<TransactionEntity>> = transactionDao.observeTransactions(startMillis, endMillis, accountId)

    fun observeSummary(
        startMillis: Long,
        endMillis: Long,
        accountId: String? = null
    ): Flow<IncomeOutgoingSummary> = transactionDao.observeSummary(startMillis, endMillis, accountId)

    fun observeCategoryBreakdown(
        startMillis: Long,
        endMillis: Long,
        accountId: String? = null
    ): Flow<List<CategoryBreakdownRow>> =
        transactionDao.observeCategoryBreakdown(startMillis, endMillis, accountId)

    fun observePendingUnparsed(): Flow<List<UnparsedSmsEntity>> = unparsedSmsDao.observePending()

    fun observePendingUnparsedCount(): Flow<Int> = unparsedSmsDao.observePendingCount()

    fun observeCategoryRules(): Flow<List<CategoryRuleEntity>> = categoryRuleDao.observeAllRules()

    suspend fun getTransactionsSnapshot(
        startMillis: Long,
        endMillis: Long,
        accountId: String? = null
    ): List<TransactionEntity> = withContext(Dispatchers.IO) {
        transactionDao.getTransactionsSnapshot(startMillis, endMillis, accountId)
    }

    suspend fun syncInbox(overrideLookbackDays: Int? = null): SyncSummary = withContext(Dispatchers.IO) {
        if (!smsInboxReader.hasReadSmsPermission()) {
            return@withContext SyncSummary(0, 0, 0, 0, permissionMissing = true)
        }

        syncMutex.withLock {
            val days = overrideLookbackDays ?: preferences.settings.value.syncLookbackDays
            val sinceMillis = if (days <= 0) {
                0L
            } else {
                System.currentTimeMillis() - days.toLong() * 86_400_000L
            }

            val messages = smsInboxReader.readInboxMessages(sinceMillis)
            val rules = categoryRuleDao.getEnabledRules()

            var newlyParsed = 0
            var duplicates = 0
            var newlyUnparsed = 0

            for (msg in messages) {
                when (val outcome = parserEngine.processSms(msg.sender, msg.body, msg.timestampMillis, rules)) {
                    is ParseOutcome.Parsed -> {
                        val insertedId = persistParsedTransaction(outcome.transaction, outcome.assignedCategory)
                        if (insertedId != -1L) newlyParsed++ else duplicates++
                    }
                    is ParseOutcome.Unparsed -> {
                        val rowId = unparsedSmsDao.insertIgnore(
                            UnparsedSmsEntity(
                                dedupeHash = outcome.dedupeHash,
                                sender = outcome.sender,
                                body = outcome.body,
                                receivedAtMillis = outcome.receivedAtMillis,
                                failureReason = outcome.reason
                            )
                        )
                        if (rowId != -1L) newlyUnparsed++
                    }
                    is ParseOutcome.Ignored -> Unit
                }
            }

            preferences.setLastSyncMillis(System.currentTimeMillis())

            SyncSummary(
                scannedCount = messages.size,
                newlyParsedCount = newlyParsed,
                duplicateCount = duplicates,
                newlyUnparsedCount = newlyUnparsed
            )
        }
    }

    suspend fun ingestRealtimeSms(
        sender: String,
        body: String,
        receivedAtMillis: Long
    ) = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val rules = categoryRuleDao.getEnabledRules()
            when (val outcome = parserEngine.processSms(sender, body, receivedAtMillis, rules)) {
                is ParseOutcome.Parsed -> {
                    val insertedId = persistParsedTransaction(outcome.transaction, outcome.assignedCategory)
                    if (insertedId != -1L) {
                        val savedTx = transactionDao.getById(insertedId)
                        if (savedTx != null) {
                            val monthRange = currentMonthRangeMillis()
                            val monthSummary = transactionDao.observeSummary(
                                monthRange.first,
                                monthRange.second,
                                null
                            ).first()
                            notificationHelper.notifyNewTransaction(
                                savedTx,
                                monthSummary.totalOutgoingMinor
                            )
                        }
                    }
                }
                is ParseOutcome.Unparsed -> {
                    unparsedSmsDao.insertIgnore(
                        UnparsedSmsEntity(
                            dedupeHash = outcome.dedupeHash,
                            sender = outcome.sender,
                            body = outcome.body,
                            receivedAtMillis = outcome.receivedAtMillis,
                            failureReason = outcome.reason
                        )
                    )
                }
                is ParseOutcome.Ignored -> Unit
            }
        }
    }

    private suspend fun persistParsedTransaction(
        parsed: ParsedTransaction,
        category: String
    ): Long {
        val account = AccountEntity(
            id = parsed.accountId,
            institutionName = parsed.institutionName,
            accountIdentifier = parsed.accountIdentifier,
            displayName = parsed.accountDisplayName,
            accountType = parsed.accountType,
            currency = parsed.currency,
            currentBalanceMinor = parsed.balanceAfterMinor ?: 0L,
            lastUpdatedMillis = parsed.timestampMillis
        )
        val entity = TransactionEntity(
            dedupeKey = parsed.dedupeKey,
            accountId = parsed.accountId,
            type = parsed.type,
            source = TransactionSource.SMS,
            amountMinor = parsed.amountMinor,
            serviceFeeMinor = parsed.serviceFeeMinor,
            vatMinor = parsed.vatMinor,
            extraFeeMinor = parsed.extraFeeMinor,
            feeMinor = parsed.feeMinor,
            totalAmountMinor = parsed.totalAmountMinor,
            balanceAfterMinor = parsed.balanceAfterMinor,
            currency = parsed.currency,
            timestampMillis = parsed.timestampMillis,
            counterparty = parsed.counterparty,
            counterpartyAccount = parsed.counterpartyAccount,
            referenceId = parsed.referenceId,
            receiptUrl = parsed.receiptUrl,
            category = category,
            isSelfTransfer = parsed.suggestedSelfTransfer,
            smsSender = parsed.smsSender,
            rawSmsBody = parsed.rawSmsBody
        )
        return transactionDao.insertWithAccountAndBalance(accountDao, account, entity)
    }

    suspend fun addManualCashTransaction(
        type: TransactionType,
        amountMinor: Long,
        category: String,
        counterparty: String?,
        note: String?,
        timestampMillis: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val existingCash = accountDao.getAccountById(CASH_ACCOUNT_ID)
        val currentCashBalance = existingCash?.currentBalanceMinor ?: 0L
        val nextCashBalance = if (type == TransactionType.INCOME) {
            currentCashBalance + amountMinor
        } else {
            currentCashBalance - amountMinor
        }

        val cashAccount = AccountEntity(
            id = CASH_ACCOUNT_ID,
            institutionName = "Cash",
            accountIdentifier = "WALLET",
            displayName = "Cash Wallet",
            accountType = AccountType.CASH,
            currency = "ETB",
            currentBalanceMinor = nextCashBalance,
            lastUpdatedMillis = timestampMillis
        )
        val tx = TransactionEntity(
            dedupeKey = "MANUAL:${UUID.randomUUID()}",
            accountId = CASH_ACCOUNT_ID,
            type = type,
            source = TransactionSource.MANUAL_CASH,
            amountMinor = amountMinor,
            totalAmountMinor = amountMinor,
            balanceAfterMinor = nextCashBalance,
            timestampMillis = timestampMillis,
            counterparty = counterparty?.takeIf { it.isNotBlank() } ?: "Cash",
            category = category,
            note = note?.takeIf { it.isNotBlank() }
        )
        transactionDao.insertWithAccountAndBalance(accountDao, cashAccount, tx)
    }

    suspend fun convertUnparsedToTransaction(
        unparsedId: Long,
        accountName: String,
        type: TransactionType,
        amountMinor: Long,
        feeMinor: Long,
        category: String,
        counterparty: String?,
        isSelfTransfer: Boolean,
        note: String?
    ) = withContext(Dispatchers.IO) {
        val unparsed = unparsedSmsDao.getById(unparsedId) ?: return@withContext
        val normalizedAcctId = "MANUAL_${accountName.uppercase().replace(Regex("[^A-Z0-9]"), "_")}"
        val account = AccountEntity(
            id = normalizedAcctId,
            institutionName = accountName.trim().ifBlank { "Bank" },
            accountIdentifier = accountName.trim().ifBlank { "Manual" },
            displayName = accountName.trim().ifBlank { "Bank Account" },
            accountType = AccountType.BANK,
            currency = "ETB",
            currentBalanceMinor = 0L,
            lastUpdatedMillis = unparsed.receivedAtMillis,
            isIncludedInTotal = false
        )
        val total = if (type == TransactionType.OUTGOING) amountMinor + feeMinor else amountMinor
        val tx = TransactionEntity(
            dedupeKey = "UNPARSED:${unparsed.dedupeHash}",
            accountId = account.id,
            type = type,
            source = TransactionSource.MANUAL_FROM_UNPARSED,
            amountMinor = amountMinor,
            feeMinor = feeMinor,
            totalAmountMinor = total,
            timestampMillis = unparsed.receivedAtMillis,
            counterparty = counterparty?.takeIf { it.isNotBlank() } ?: unparsed.sender,
            category = category,
            isSelfTransfer = isSelfTransfer,
            note = note?.takeIf { it.isNotBlank() },
            smsSender = unparsed.sender,
            rawSmsBody = unparsed.body
        )
        val insertedId = transactionDao.insertWithAccountAndBalance(accountDao, account, tx)
        if (insertedId != -1L) {
            unparsedSmsDao.updateStatus(unparsedId, UnparsedStatus.CONVERTED, insertedId)
        }
    }

    suspend fun dismissUnparsedSms(unparsedId: Long) = withContext(Dispatchers.IO) {
        unparsedSmsDao.updateStatus(unparsedId, UnparsedStatus.DISMISSED, null)
    }

    suspend fun toggleSelfTransfer(transactionId: Long, isSelfTransfer: Boolean) =
        withContext(Dispatchers.IO) {
            transactionDao.setSelfTransfer(transactionId, isSelfTransfer)
        }

    suspend fun updateTransactionDetails(
        transactionId: Long,
        category: String,
        note: String?
    ) = withContext(Dispatchers.IO) {
        transactionDao.updateCategoryAndNote(transactionId, category, note?.takeIf { it.isNotBlank() })
    }

    suspend fun deleteTransaction(transactionId: Long) = withContext(Dispatchers.IO) {
        transactionDao.deleteById(transactionId)
    }

    suspend fun setAccountIncludedInTotal(accountId: String, included: Boolean) =
        withContext(Dispatchers.IO) {
            accountDao.setIncludedInTotal(accountId, included)
        }

    suspend fun addCategoryRule(
        keyword: String,
        category: String,
        matchField: RuleMatchField,
        typeFilter: TransactionType?,
        priority: Int
    ) = withContext(Dispatchers.IO) {
        categoryRuleDao.upsert(
            CategoryRuleEntity(
                keyword = keyword.trim(),
                category = category.trim(),
                matchField = matchField,
                transactionTypeFilter = typeFilter,
                priority = priority,
                isEnabled = true
            )
        )
    }

    suspend fun setCategoryRuleEnabled(ruleId: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        categoryRuleDao.setEnabled(ruleId, enabled)
    }

    suspend fun deleteCategoryRule(ruleId: Long) = withContext(Dispatchers.IO) {
        categoryRuleDao.deleteById(ruleId)
    }

    suspend fun reapplyRulesToUncategorized(): Int = withContext(Dispatchers.IO) {
        val rules = categoryRuleDao.getEnabledRules()
        val uncategorized = transactionDao.getUncategorizedTransactions()
        var updated = 0
        for (tx in uncategorized) {
            val matched = parserEngine.resolveCategoryFields(
                type = tx.type,
                counterparty = tx.counterparty,
                rawBody = tx.rawSmsBody.orEmpty(),
                rules = rules
            )
            if (matched != "Uncategorized") {
                transactionDao.updateCategory(tx.id, matched)
                updated++
            }
        }
        updated
    }

    private fun currentMonthRangeMillis(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        val start = now.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        return start to end
    }

    companion object {
        const val CASH_ACCOUNT_ID = "CASH_ETB"
    }
}
