package com.yourname.etbledger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yourname.etbledger.data.local.UnparsedStatus

@Entity(
    tableName = "unparsed_sms",
    indices = [
        Index(value = ["dedupeHash"], unique = true),
        Index(value = ["status", "receivedAtMillis"])
    ]
)
data class UnparsedSmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dedupeHash: String,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val failureReason: String? = null,
    val status: UnparsedStatus = UnparsedStatus.PENDING,
    val linkedTransactionId: Long? = null
)
