package com.yourname.etbledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourname.etbledger.data.local.AccountType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val institutionName: String,
    val accountIdentifier: String,
    val displayName: String,
    val accountType: AccountType,
    val currency: String = "ETB",
    val currentBalanceMinor: Long = 0L,
    val lastUpdatedMillis: Long = 0L,
    val isIncludedInTotal: Boolean = true
)
