package com.yourname.etbledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yourname.etbledger.data.local.UnparsedStatus
import com.yourname.etbledger.data.local.entity.UnparsedSmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnparsedSmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sms: UnparsedSmsEntity): Long

    @Query("SELECT * FROM unparsed_sms WHERE status = 'PENDING' ORDER BY receivedAtMillis DESC")
    fun observePending(): Flow<List<UnparsedSmsEntity>>

    @Query("SELECT COUNT(*) FROM unparsed_sms WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM unparsed_sms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UnparsedSmsEntity?

    @Query("UPDATE unparsed_sms SET status = :status, linkedTransactionId = :txId WHERE id = :id")
    suspend fun updateStatus(id: Long, status: UnparsedStatus, txId: Long? = null)
}
