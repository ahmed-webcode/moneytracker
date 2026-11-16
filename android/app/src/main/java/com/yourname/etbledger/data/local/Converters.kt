package com.yourname.etbledger.data.local

import androidx.room.TypeConverter

enum class AccountType {
    BANK,
    MOBILE_MONEY,
    CASH
}

enum class TransactionType {
    INCOME,
    OUTGOING
}

enum class TransactionSource {
    SMS,
    MANUAL_CASH,
    MANUAL_FROM_UNPARSED
}

enum class RuleMatchField {
    ANY,
    COUNTERPARTY,
    RAW_BODY
}

enum class UnparsedStatus {
    PENDING,
    CONVERTED,
    DISMISSED
}

class Converters {
    @TypeConverter fun fromAccountType(value: AccountType): String = value.name
    @TypeConverter fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter fun fromTransactionType(value: TransactionType): String = value.name
    @TypeConverter fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter fun fromTransactionSource(value: TransactionSource): String = value.name
    @TypeConverter fun toTransactionSource(value: String): TransactionSource = TransactionSource.valueOf(value)

    @TypeConverter fun fromRuleMatchField(value: RuleMatchField): String = value.name
    @TypeConverter fun toRuleMatchField(value: String): RuleMatchField = RuleMatchField.valueOf(value)

    @TypeConverter fun fromUnparsedStatus(value: UnparsedStatus): String = value.name
    @TypeConverter fun toUnparsedStatus(value: String): UnparsedStatus = UnparsedStatus.valueOf(value)
}
