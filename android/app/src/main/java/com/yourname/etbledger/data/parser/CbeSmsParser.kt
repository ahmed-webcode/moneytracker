package com.yourname.etbledger.data.parser

import com.yourname.etbledger.data.local.AccountType
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.domain.model.ParsedTransaction
import javax.inject.Inject

class CbeSmsParser @Inject constructor() : BankSmsParser {

    override val bankName: String = "CBE"

    private val receiptRegex =
        Regex("""https://mbrec(?:ei|ie)pt\.cbe\.com\.et/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)

    private val receiveRegex = Regex(
        pattern = """You have received\s+ETB\s+([\d,]+\.\d{2})\s+from account\s+(\S+)\s+\(([^)]+)\)\s+to your account\s+([^.\s]+)\.\s+Your current balance is\s+ETB\s+([\d,]+\.\d{2})""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val transferRegex = Regex(
        pattern = """You have successfully transferred\s+ETB\s+([\d,]+\.\d{2})\s+from account\s+(\S+)\s+to account\s+(\S+)\s+\(([^)]+)\)\.\s*(?:Service charge of\s+(?:ETB\s+)?([\d,]+\.\d{2})\s+and\s+VAT\(15%\)\s+of\s+(?:ETB\s+)?([\d,]+\.\d{2})(?:\s+and\s+Disaster Recovery\(5%\)\s+of\s+(?:ETB\s+)?([\d,]+\.\d{2}))?\s+with total of\s+(?:ETB\s+)?([\d,]+\.\d{2})\.\s*)?Your current balance is\s+ETB\s+([\d,]+\.\d{2})""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    override fun canHandle(sender: String, body: String): Boolean {
        val s = sender.trim().lowercase()
        val b = body.lowercase()
        return s.contains("cbe") ||
            b.contains("banking with cbe") ||
            b.contains("mbreciept.cbe.com.et") ||
            b.contains("mbreceipt.cbe.com.et")
    }

    override fun parse(sender: String, body: String, smsTimestampMillis: Long): ParsedTransaction? {
        val receiptMatch = receiptRegex.find(body)
        val receiptUrl = receiptMatch?.value
        val referenceId = receiptMatch?.groupValues?.getOrNull(1)

        receiveRegex.find(body)?.let { m ->
            val amountMinor = MoneyUtils.parseEtbToMinor(m.groupValues[1])
            val senderAcct = m.groupValues[2].trim()
            val senderName = MoneyUtils.normalizeSpaces(m.groupValues[3])
            val myAccount = m.groupValues[4].trim()
            val balanceMinor = MoneyUtils.parseEtbToMinor(m.groupValues[5])

            return ParsedTransaction(
                dedupeKey = MoneyUtils.buildSmsDedupeKey("CBE", referenceId, sender, body),
                accountId = "CBE_$myAccount",
                institutionName = "CBE",
                accountIdentifier = myAccount,
                accountDisplayName = "CBE ($myAccount)",
                accountType = AccountType.BANK,
                type = TransactionType.INCOME,
                amountMinor = amountMinor,
                balanceAfterMinor = balanceMinor,
                timestampMillis = smsTimestampMillis,
                counterparty = senderName,
                counterpartyAccount = senderAcct,
                referenceId = referenceId,
                receiptUrl = receiptUrl,
                smsSender = sender,
                rawSmsBody = body
            )
        }

        transferRegex.find(body)?.let { m ->
            val amountMinor = MoneyUtils.parseEtbToMinor(m.groupValues[1])
            val myAccount = m.groupValues[2].trim()
            val recipientAcct = m.groupValues[3].trim()
            val recipientName = MoneyUtils.normalizeSpaces(m.groupValues[4])

            val serviceFeeMinor = m.groupValues[5].takeIf { it.isNotBlank() }
                ?.let { MoneyUtils.parseEtbToMinor(it) } ?: 0L
            val vatMinor = m.groupValues[6].takeIf { it.isNotBlank() }
                ?.let { MoneyUtils.parseEtbToMinor(it) } ?: 0L
            val disasterMinor = m.groupValues[7].takeIf { it.isNotBlank() }
                ?.let { MoneyUtils.parseEtbToMinor(it) } ?: 0L
            val totalFeeMinor = serviceFeeMinor + vatMinor + disasterMinor

            val reportedTotalMinor = m.groupValues[8].takeIf { it.isNotBlank() }
                ?.let { MoneyUtils.parseEtbToMinor(it) } ?: (amountMinor + totalFeeMinor)
            val balanceMinor = MoneyUtils.parseEtbToMinor(m.groupValues[9])

            return ParsedTransaction(
                dedupeKey = MoneyUtils.buildSmsDedupeKey("CBE", referenceId, sender, body),
                accountId = "CBE_$myAccount",
                institutionName = "CBE",
                accountIdentifier = myAccount,
                accountDisplayName = "CBE ($myAccount)",
                accountType = AccountType.BANK,
                type = TransactionType.OUTGOING,
                amountMinor = amountMinor,
                serviceFeeMinor = serviceFeeMinor,
                vatMinor = vatMinor,
                extraFeeMinor = disasterMinor,
                feeMinor = totalFeeMinor,
                totalAmountMinor = reportedTotalMinor,
                balanceAfterMinor = balanceMinor,
                timestampMillis = smsTimestampMillis,
                counterparty = recipientName,
                counterpartyAccount = recipientAcct,
                referenceId = referenceId,
                receiptUrl = receiptUrl,
                smsSender = sender,
                rawSmsBody = body
            )
        }

        return null
    }
}
