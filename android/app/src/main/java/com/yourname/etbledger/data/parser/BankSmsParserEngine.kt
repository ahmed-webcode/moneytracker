package com.yourname.etbledger.data.parser

import com.yourname.etbledger.data.local.RuleMatchField
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.entity.CategoryRuleEntity
import com.yourname.etbledger.domain.model.ParseOutcome
import com.yourname.etbledger.domain.model.ParsedTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankSmsParserEngine @Inject constructor(
    private val parsers: Set<@JvmSuppressWildcards BankSmsParser>,
    private val noiseFilter: SmsNoiseFilter
) {
    fun processSms(
        sender: String,
        body: String,
        receivedAtMillis: Long,
        categoryRules: List<CategoryRuleEntity>
    ): ParseOutcome {
        if (!noiseFilter.isTrackedFinancialSender(sender, body)) {
            return ParseOutcome.Ignored("Not a tracked bank/wallet SMS")
        }

        noiseFilter.getIgnoreReason(body)?.let { reason ->
            return ParseOutcome.Ignored(reason)
        }

        val matchingParsers = parsers.filter { it.canHandle(sender, body) }
        if (matchingParsers.isEmpty()) {
            return ParseOutcome.Unparsed(
                sender = sender,
                body = body,
                receivedAtMillis = receivedAtMillis,
                dedupeHash = MoneyUtils.buildUnparsedDedupeHash(sender, body),
                reason = "No parser registered for sender '$sender'"
            )
        }

        for (parser in matchingParsers) {
            val parsed = parser.parse(sender, body, receivedAtMillis)
            if (parsed != null) {
                val category = resolveCategory(parsed, categoryRules)
                return ParseOutcome.Parsed(
                    transaction = parsed,
                    assignedCategory = category
                )
            }
        }

        return ParseOutcome.Unparsed(
            sender = sender,
            body = body,
            receivedAtMillis = receivedAtMillis,
            dedupeHash = MoneyUtils.buildUnparsedDedupeHash(sender, body),
            reason = "Matched ${matchingParsers.joinToString { it.bankName }} but no regex pattern matched"
        )
    }

    fun resolveCategory(
        tx: ParsedTransaction,
        rules: List<CategoryRuleEntity>
    ): String = resolveCategoryFields(
        type = tx.type,
        counterparty = tx.counterparty,
        rawBody = tx.rawSmsBody,
        rules = rules
    )

    fun resolveCategoryFields(
        type: TransactionType,
        counterparty: String?,
        rawBody: String,
        rules: List<CategoryRuleEntity>
    ): String {
        val sortedRules = rules
            .filter { it.isEnabled }
            .sortedWith(compareByDescending<CategoryRuleEntity> { it.priority }.thenBy { it.id })

        for (rule in sortedRules) {
            if (rule.transactionTypeFilter != null && rule.transactionTypeFilter != type) {
                continue
            }
            val keyword = rule.keyword.trim()
            if (keyword.isEmpty()) continue

            val matched = when (rule.matchField) {
                RuleMatchField.COUNTERPARTY ->
                    counterparty?.contains(keyword, ignoreCase = true) == true
                RuleMatchField.RAW_BODY ->
                    rawBody.contains(keyword, ignoreCase = true)
                RuleMatchField.ANY ->
                    counterparty?.contains(keyword, ignoreCase = true) == true ||
                        rawBody.contains(keyword, ignoreCase = true)
            }
            if (matched) return rule.category
        }
        return "Uncategorized"
    }
}
