package com.yourname.etbledger.data.parser

import com.yourname.etbledger.data.local.AccountType
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.domain.model.ParsedTransaction
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class TelebirrSmsParser(
    private val zoneId: ZoneId = ZoneId.of("Africa/Addis_Ababa")
) : BankSmsParser {

    @Inject
    constructor() : this(ZoneId.of("Africa/Addis_Ababa"))

    override val bankName: String = "telebirr"

    private val isoDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val slashDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    private val receiptUrlRegex =
        Regex("""https://transactioninfo\.ethiotelecom\.et/receipt/[A-Za-z0-9_-]+""")

    private val receiveRegex = Regex(
        pattern = """You have received\s+ETB\s+([\d,]+\.\d{2})\s+by transaction number\s+([A-Z0-9]+)\s+on\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}|\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})\s+from\s+(.+?)\s+to your telebirr Account\s+(\S+)\s+-\s+[^.]+\.\s+Your current(?:\s+E-Money\s+Account)?\s+balance is\s+ETB\s+([\d,]+\.\d{2})""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val transferRegex = Regex(
        pattern = """You have transferred\s+ETB\s+([\d,]+\.\d{2})\s+to\s+([^()\r\n]+?)(?:\s*\(([^)]+)\))?\s+on\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2}|\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\.\s+Your transaction number is\s+([A-Z0-9]+)\.\s+(?:The service fee is\s+ETB\s+([\d,]+\.\d{2})\s+and\s+15%\s+VAT on the service fee is\s+ETB\s+([\d,]+\.\d{2})\.\s+)?Your current(?:\s+E-Money\s+Account)?\s+balance is\s+ETB\s+([\d,]+\.\d{2})""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val packagePaymentRegex = Regex(
        pattern = """You have paid\s+ETB\s+([\d,]+\.\d{2})\s+for\s+package\s+(.+?)\s+purchase made for\s+(\S+)\s+on\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2}|\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\.\s+Your transaction number is\s+([A-Z0-9]+)\.\s+Your current(?:\s+E-Money\s+Account)?\s+balance is\s+ETB\s+([\d,]+\.\d{2})""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    override fun canHandle(sender: String, body: String): Boolean {
        val s = sender.trim().lowercase()
        val b = body.lowercase()
        return s.contains("telebirr") ||
            s == "127" ||
            b.contains("using telebirr") ||
            b.contains("transactioninfo.ethiotelecom.et")
    }

    override fun parse(sender: String, body: String, smsTimestampMillis: Long): ParsedTransaction? {
        val receiptUrl = receiptUrlRegex.find(body)?.value

        receiveRegex.find(body)?.let { m ->
            val amountMinor = MoneyUtils.parseEtbToMinor(m.groupValues[1])
            val refId = m.groupValues[2].trim()
            val txTime = parseTelebirrTimestamp(m.groupValues[3], smsTimestampMillis)
            val fromSource = MoneyUtils.normalizeSpaces(m.groupValues[4])
            val accountMsisdn = normalizeMsisdn(m.groupValues[5])
            val balanceMinor = MoneyUtils.parseEtbToMinor(m.groupValues[6])

            val isFromOwnCbe = fromSource.contains("Commercial Bank of Ethiopia", ignoreCase = true)

            return ParsedTransaction(
                dedupeKey = MoneyUtils.buildSmsDedupeKey("TELEBIRR", refId, sender, body),
                accountId = CANONICAL_ACCOUNT_ID,
                institutionName = "telebirr",
                accountIdentifier = accountMsisdn,
                accountDisplayName = "telebirr Wallet",
                accountType = AccountType.MOBILE_MONEY,
                type = TransactionType.INCOME,
                amountMinor = amountMinor,
                balanceAfterMinor = balanceMinor,
                timestampMillis = txTime,
                counterparty = fromSource,
                referenceId = refId,
                receiptUrl = receiptUrl,
                suggestedSelfTransfer = isFromOwnCbe,
                smsSender = sender,
                rawSmsBody = body
            )
        }

        transferRegex.find(body)?.let { m ->
            val amountMinor = MoneyUtils.parseEtbToMinor(m.groupValues[1])
            val recipientName = MoneyUtils.normalizeSpaces(m.groupValues[2])
            val recipientPhone = m.groupValues[3].takeIf { it.isNotBlank() }?.trim()
            val txTime = parseTelebirrTimestamp(m.groupValues[4], smsTimestampMillis)
            val refId = m.groupValues[5].trim()
            val serviceFeeMinor = m.groupValues[6].takeIf { it.isNotBlank() }
                ?.let { MoneyUtils.parseEtbToMinor(it) } ?: 0L
            val vatMinor = m.groupValues[7].takeIf { it.isNotBlank() }
                ?.let { MoneyUtils.parseEtbToMinor(it) } ?: 0L
            val balanceMinor = MoneyUtils.parseEtbToMinor(m.groupValues[8])
            val totalFee = serviceFeeMinor + vatMinor

            return ParsedTransaction(
                dedupeKey = MoneyUtils.buildSmsDedupeKey("TELEBIRR", refId, sender, body),
                accountId = CANONICAL_ACCOUNT_ID,
                institutionName = "telebirr",
                accountIdentifier = "E-Money",
                accountDisplayName = "telebirr Wallet",
                accountType = AccountType.MOBILE_MONEY,
                type = TransactionType.OUTGOING,
                amountMinor = amountMinor,
                serviceFeeMinor = serviceFeeMinor,
                vatMinor = vatMinor,
                feeMinor = totalFee,
                totalAmountMinor = amountMinor + totalFee,
                balanceAfterMinor = balanceMinor,
                timestampMillis = txTime,
                counterparty = recipientName,
                counterpartyAccount = recipientPhone,
                referenceId = refId,
                receiptUrl = receiptUrl,
                smsSender = sender,
                rawSmsBody = body
            )
        }

        packagePaymentRegex.find(body)?.let { m ->
            val amountMinor = MoneyUtils.parseEtbToMinor(m.groupValues[1])
            val packageName = MoneyUtils.normalizeSpaces(m.groupValues[2])
            val targetNumber = normalizeMsisdn(m.groupValues[3])
            val txTime = parseTelebirrTimestamp(m.groupValues[4], smsTimestampMillis)
            val refId = m.groupValues[5].trim()
            val balanceMinor = MoneyUtils.parseEtbToMinor(m.groupValues[6])

            return ParsedTransaction(
                dedupeKey = MoneyUtils.buildSmsDedupeKey("TELEBIRR", refId, sender, body),
                accountId = CANONICAL_ACCOUNT_ID,
                institutionName = "telebirr",
                accountIdentifier = targetNumber,
                accountDisplayName = "telebirr Wallet",
                accountType = AccountType.MOBILE_MONEY,
                type = TransactionType.OUTGOING,
                amountMinor = amountMinor,
                balanceAfterMinor = balanceMinor,
                timestampMillis = txTime,
                counterparty = packageName,
                counterpartyAccount = targetNumber,
                referenceId = refId,
                receiptUrl = receiptUrl,
                smsSender = sender,
                rawSmsBody = body
            )
        }

        return null
    }

    private fun parseTelebirrTimestamp(rawDate: String, fallbackMillis: Long): Long {
        val cleaned = MoneyUtils.normalizeSpaces(rawDate)
        return runCatching {
            val ldt = if (cleaned.contains("/")) {
                LocalDateTime.parse(cleaned, slashDateFormat)
            } else {
                LocalDateTime.parse(cleaned, isoDateFormat)
            }
            ldt.atZone(zoneId).toInstant().toEpochMilli()
        }.getOrDefault(fallbackMillis)
    }

    private fun normalizeMsisdn(raw: String): String {
        val digits = raw.trim()
        return when {
            digits.startsWith("9") && digits.length == 9 -> "251$digits"
            digits.startsWith("09") && digits.length == 10 -> "251${digits.drop(1)}"
            else -> digits
        }
    }

    companion object {
        const val CANONICAL_ACCOUNT_ID = "TELEBIRR_MAIN"
    }
}
