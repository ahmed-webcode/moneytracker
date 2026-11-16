package com.yourname.etbledger.data.parser

import com.yourname.etbledger.data.local.RuleMatchField
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.entity.CategoryRuleEntity
import com.yourname.etbledger.domain.model.ParseOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSmsParserEngineTest {

    private val engine = BankSmsParserEngine(
        parsers = setOf(TelebirrSmsParser(), CbeSmsParser()),
        noiseFilter = SmsNoiseFilter()
    )

    private val rules = listOf(
        CategoryRuleEntity(
            id = 1,
            keyword = "Internet",
            category = "Telecom & Data",
            matchField = RuleMatchField.ANY,
            transactionTypeFilter = TransactionType.OUTGOING,
            priority = 10
        ),
        CategoryRuleEntity(
            id = 2,
            keyword = "transferred",
            category = "Transfers",
            matchField = RuleMatchField.RAW_BODY,
            transactionTypeFilter = TransactionType.OUTGOING,
            priority = 1
        )
    )

    @Test
    fun `ignores non-bank personal SMS`() {
        val outcome = engine.processSms("+251911223344", "Hey, meet me at 5pm", 1000L, rules)
        assertTrue(outcome is ParseOutcome.Ignored)
    }

    @Test
    fun `ignores CBE OTP SMS`() {
        val outcome = engine.processSms(
            "CBE",
            "Your OTP code is 492817. Do not share this code with anyone.",
            1000L,
            rules
        )
        assertTrue(outcome is ParseOutcome.Ignored)
    }

    @Test
    fun `queues unknown bank SMS into Unparsed`() {
        val outcome = engine.processSms(
            "CBE",
            "Dear Ahmed, ETB 500.00 was debited for ATM withdrawal at Bole branch.",
            1000L,
            rules
        )
        assertTrue(outcome is ParseOutcome.Unparsed)
    }

    @Test
    fun `auto-categorizes telebirr internet package`() {
        val sms = "Dear Ahmed\nYou have paid ETB 37.00 for package Internet Daily  Package 1.2GB purchase made for 920928989 on 15/11/2026 06:14:16. Your transaction number is  DKF67EJ2AY. Your current balance is ETB 73.86."
        val outcome = engine.processSms("telebirr", sms, 1000L, rules)
        assertTrue(outcome is ParseOutcome.Parsed)
        assertEquals("Telecom & Data", (outcome as ParseOutcome.Parsed).assignedCategory)
    }

    @Test
    fun `dedupeKey is identical between BroadcastReceiver and Inbox timestamps`() {
        val sms = "Dear Ahmed Muhammed Seid You have received ETB 4,000.00 from account 1**1475 (Abdulhay Adem Guadie) to your account 1**8265. Your current balance is ETB 5,672.28. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxNXPQY6wJEtVjsYL"
        val r1 = engine.processSms("CBE", sms, 1763280000120L, rules) as ParseOutcome.Parsed
        val r2 = engine.processSms("CBE", sms, 1763280000950L, rules) as ParseOutcome.Parsed
        assertEquals(r1.transaction.dedupeKey, r2.transaction.dedupeKey)
    }
}
