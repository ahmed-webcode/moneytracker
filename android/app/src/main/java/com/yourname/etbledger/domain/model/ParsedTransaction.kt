package com.yourname.etbledger.domain.model

import com.yourname.etbledger.data.local.AccountType
import com.yourname.etbledger.data.local.TransactionType

data class ParsedTransaction(
    val dedupeKey: String,
    val accountId: String,
    val institutionName: String,
    val accountIdentifier: String,
    val accountDisplayName: String,
    val accountType: AccountType,
    val type: TransactionType,
    val amountMinor: Long,
    val serviceFeeMinor: Long = 0L,
    val vatMinor: Long = 0L,
    val extraFeeMinor: Long = 0L,
    val feeMinor: Long = serviceFeeMinor + vatMinor + extraFeeMinor,
    val totalAmountMinor: Long = if (type == TransactionType.OUTGOING) amountMinor + feeMinor else amountMinor,
    val balanceAfterMinor: Long? = null,
    val currency: String = "ETB",
    val timestampMillis: Long,
    val counterparty: String? = null,
    val counterpartyAccount: String? = null,
    val referenceId: String? = null,
    val receiptUrl: String? = null,
    val suggestedSelfTransfer: Boolean = false,
    val smsSender: String,
    val rawSmsBody: String
)

sealed interface ParseOutcome {
    data class Parsed(
        val transaction: ParsedTransaction,
        val assignedCategory: String
    ) : ParseOutcome

    data class Unparsed(
        val sender: String,
        val body: String,
        val receivedAtMillis: Long,
        val dedupeHash: String,
        val reason: String
    ) : ParseOutcome

    data class Ignored(val reason: String) : ParseOutcome
}
