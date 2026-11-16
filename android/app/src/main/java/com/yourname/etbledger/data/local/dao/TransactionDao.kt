package com.yourname.etbledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yourname.etbledger.data.local.entity.AccountEntity
import com.yourname.etbledger.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class IncomeOutgoingSummary(
    val totalIncomeMinor: Long,
    val totalOutgoingMinor: Long,
    val totalFeesMinor: Long
)

data class CategoryBreakdownRow(
    val category: String,
    val totalMinor: Long
)

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getById(transactionId: Long): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE (:accountId IS NULL OR accountId = :accountId)
          AND timestampMillis BETWEEN :startMillis AND :endMillis
        ORDER BY timestampMillis DESC, id DESC
    """)
    fun observeTransactions(
        startMillis: Long,
        endMillis: Long,
        accountId: String? = null
    ): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE (:accountId IS NULL OR accountId = :accountId)
          AND timestampMillis BETWEEN :startMillis AND :endMillis
        ORDER BY timestampMillis DESC, id DESC
    """)
    suspend fun getTransactionsSnapshot(
        startMillis: Long,
        endMillis: Long,
        accountId: String? = null
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC, id DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE category = 'Uncategorized'")
    suspend fun getUncategorizedTransactions(): List<TransactionEntity>

    @Query("""
        SELECT 
            COALESCE(SUM(CASE WHEN type = 'INCOME' AND isSelfTransfer = 0 THEN amountMinor ELSE 0 END), 0) AS totalIncomeMinor,
            COALESCE(SUM(CASE WHEN type = 'OUTGOING' AND isSelfTransfer = 0 THEN totalAmountMinor ELSE 0 END), 0) AS totalOutgoingMinor,
            COALESCE(SUM(CASE WHEN type = 'OUTGOING' THEN feeMinor ELSE 0 END), 0) AS totalFeesMinor
        FROM transactions
        WHERE (:accountId IS NULL OR accountId = :accountId)
          AND timestampMillis BETWEEN :startMillis AND :endMillis
    """)
    fun observeSummary(
        startMillis: Long,
        endMillis: Long,
        accountId: String? = null
    ): Flow<IncomeOutgoingSummary>

    @Query("""
        SELECT category, COALESCE(SUM(totalAmountMinor), 0) AS totalMinor
        FROM transactions
        WHERE type = 'OUTGOING'
          AND isSelfTransfer = 0
          AND (:accountId IS NULL OR accountId = :accountId)
          AND timestampMillis BETWEEN :startMillis AND :endMillis
        GROUP BY category
        ORDER BY totalMinor DESC
    """)
    fun observeCategoryBreakdown(
        startMillis: Long,
        endMillis: Long,
        accountId: String? = null
    ): Flow<List<CategoryBreakdownRow>>

    @Query("UPDATE transactions SET isSelfTransfer = :isSelfTransfer WHERE id = :transactionId")
    suspend fun setSelfTransfer(transactionId: Long, isSelfTransfer: Boolean)

    @Query("UPDATE transactions SET category = :category, note = :note WHERE id = :transactionId")
    suspend fun updateCategoryAndNote(transactionId: Long, category: String, note: String?)

    @Query("UPDATE transactions SET category = :category WHERE id = :transactionId")
    suspend fun updateCategory(transactionId: Long, category: String)

    @Transaction
    suspend fun insertWithAccountAndBalance(
        accountDao: AccountDao,
        account: AccountEntity,
        transaction: TransactionEntity
    ): Long {
        accountDao.insertIfAbsent(account)
        val insertedId = insertIgnore(transaction)
        if (insertedId != -1L && transaction.balanceAfterMinor != null) {
            accountDao.updateBalanceIfNewer(
                accountId = account.id,
                balanceMinor = transaction.balanceAfterMinor,
                timestampMillis = transaction.timestampMillis
            )
        }
        return insertedId
    }
}
