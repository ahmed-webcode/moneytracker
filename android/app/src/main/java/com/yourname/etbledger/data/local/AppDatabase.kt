package com.yourname.etbledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yourname.etbledger.data.local.dao.AccountDao
import com.yourname.etbledger.data.local.dao.CategoryRuleDao
import com.yourname.etbledger.data.local.dao.TransactionDao
import com.yourname.etbledger.data.local.dao.UnparsedSmsDao
import com.yourname.etbledger.data.local.entity.AccountEntity
import com.yourname.etbledger.data.local.entity.CategoryRuleEntity
import com.yourname.etbledger.data.local.entity.TransactionEntity
import com.yourname.etbledger.data.local.entity.UnparsedSmsEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryRuleEntity::class,
        UnparsedSmsEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun unparsedSmsDao(): UnparsedSmsDao
}
