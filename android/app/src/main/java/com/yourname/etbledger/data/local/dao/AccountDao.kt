package com.yourname.etbledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yourname.etbledger.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY institutionName ASC, displayName ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT COALESCE(SUM(currentBalanceMinor), 0) FROM accounts WHERE isIncludedInTotal = 1")
    fun observeTotalBalanceMinor(): Flow<Long>

    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(account: AccountEntity): Long

    @Query("""
        UPDATE accounts 
        SET currentBalanceMinor = :balanceMinor, lastUpdatedMillis = :timestampMillis
        WHERE id = :accountId AND :timestampMillis >= lastUpdatedMillis
    """)
    suspend fun updateBalanceIfNewer(
        accountId: String,
        balanceMinor: Long,
        timestampMillis: Long
    )

    @Query("UPDATE accounts SET isIncludedInTotal = :included WHERE id = :accountId")
    suspend fun setIncludedInTotal(accountId: String, included: Boolean)
}
