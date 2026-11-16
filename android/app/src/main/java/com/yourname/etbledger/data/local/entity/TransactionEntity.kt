package com.yourname.etbledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yourname.etbledger.data.local.TransactionSource
import com.yourname.etbledger.data.local.TransactionType

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["accountId"]),
        Index(value = ["timestampMillis"]),
        Index(value = ["category"]),
        Index(value = ["type", "isSelfTransfer", "timestampMillis"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dedupeKey: String,
    val accountId: String,
    val type: TransactionType,
    val source: TransactionSource,
    val amountMinor: Long,
    val serviceFeeMinor: Long = 0L,
    val vatMinor: Long = 0L,
    val extraFeeMinor: Long = 0L,
    val feeMinor: Long = 0L,
    val totalAmountMinor: Long,
    val balanceAfterMinor: Long? = null,
    val currency: String = "ETB",
    val timestampMillis: Long,
    val counterparty: String? = null,
    val counterpartyAccount: String? = null,
    val referenceId: String? = null,
    val receiptUrl: String? = null,
    val category: String = "Uncategorized",
    val isSelfTransfer: Boolean = false,
    val note: String? = null,
    val smsSender: String? = null,
    val rawSmsBody: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)
