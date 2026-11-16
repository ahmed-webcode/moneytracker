package com.yourname.etbledger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yourname.etbledger.data.local.RuleMatchField
import com.yourname.etbledger.data.local.TransactionType

@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["keyword", "category"], unique = true)]
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val keyword: String,
    val category: String,
    val matchField: RuleMatchField = RuleMatchField.ANY,
    val transactionTypeFilter: TransactionType? = null,
    val priority: Int = 0,
    val isEnabled: Boolean = true
)
