package com.yourname.etbledger.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yourname.etbledger.data.local.AppDatabase
import com.yourname.etbledger.data.local.RuleMatchField
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.dao.AccountDao
import com.yourname.etbledger.data.local.dao.CategoryRuleDao
import com.yourname.etbledger.data.local.dao.TransactionDao
import com.yourname.etbledger.data.local.dao.UnparsedSmsDao
import com.yourname.etbledger.data.local.entity.CategoryRuleEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        categoryRuleDaoProvider: Provider<CategoryRuleDao>
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "etb_ledger.db"
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        categoryRuleDaoProvider.get().insertAllIgnore(defaultCategoryRules())
                    }
                }
            })
            .build()
    }

    @Provides fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideCategoryRuleDao(db: AppDatabase): CategoryRuleDao = db.categoryRuleDao()
    @Provides fun provideUnparsedSmsDao(db: AppDatabase): UnparsedSmsDao = db.unparsedSmsDao()

    private fun defaultCategoryRules(): List<CategoryRuleEntity> = listOf(
        CategoryRuleEntity(keyword = "package", category = "Telecom & Data", matchField = RuleMatchField.RAW_BODY, transactionTypeFilter = TransactionType.OUTGOING, priority = 10),
        CategoryRuleEntity(keyword = "Internet", category = "Telecom & Data", matchField = RuleMatchField.ANY, transactionTypeFilter = TransactionType.OUTGOING, priority = 10),
        CategoryRuleEntity(keyword = "airtime", category = "Telecom & Data", matchField = RuleMatchField.RAW_BODY, transactionTypeFilter = TransactionType.OUTGOING, priority = 10),
        CategoryRuleEntity(keyword = "electricity", category = "Bills & Utilities", matchField = RuleMatchField.RAW_BODY, transactionTypeFilter = TransactionType.OUTGOING, priority = 9),
        CategoryRuleEntity(keyword = "water", category = "Bills & Utilities", matchField = RuleMatchField.RAW_BODY, transactionTypeFilter = TransactionType.OUTGOING, priority = 9),
        CategoryRuleEntity(keyword = "ride", category = "Transport", matchField = RuleMatchField.ANY, transactionTypeFilter = TransactionType.OUTGOING, priority = 8),
        CategoryRuleEntity(keyword = "feres", category = "Transport", matchField = RuleMatchField.ANY, transactionTypeFilter = TransactionType.OUTGOING, priority = 8),
        CategoryRuleEntity(keyword = "supermarket", category = "Food & Groceries", matchField = RuleMatchField.ANY, transactionTypeFilter = TransactionType.OUTGOING, priority = 7),
        CategoryRuleEntity(keyword = "cafe", category = "Food & Groceries", matchField = RuleMatchField.ANY, transactionTypeFilter = TransactionType.OUTGOING, priority = 7),
        CategoryRuleEntity(keyword = "transferred", category = "Transfers", matchField = RuleMatchField.RAW_BODY, transactionTypeFilter = TransactionType.OUTGOING, priority = 1),
        CategoryRuleEntity(keyword = "received", category = "Income & Transfers", matchField = RuleMatchField.RAW_BODY, transactionTypeFilter = TransactionType.INCOME, priority = 1)
    )
}
