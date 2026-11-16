package com.yourname.etbledger.data.parser

import com.yourname.etbledger.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CbeSmsParserTest {

    private val parser = CbeSmsParser()

    @Test
    fun `parses CBE receive SMS with comma-formatted amount`() {
        val sms = "Dear Ahmed Muhammed Seid You have received ETB 4,000.00 from account 1**1475 (Abdulhay Adem Guadie) to your account 1**8265. Your current balance is ETB 5,672.28. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxNXPQY6wJEtVjsYL  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6"
        val fallbackTime = 1763280000000L

        val result = parser.parse("CBE", sms, fallbackTime)
        assertNotNull(result)
        result!!

        assertEquals(TransactionType.INCOME, result.type)
        assertEquals(4000_00L, result.amountMinor)
        assertEquals(5672_28L, result.balanceAfterMinor)
        assertEquals("CBE_1**8265", result.accountId)
        assertEquals("1**8265", result.accountIdentifier)
        assertEquals("Abdulhay Adem Guadie", result.counterparty)
        assertEquals("1**1475", result.counterpartyAccount)
        assertEquals("v2-hfHCxNXPQY6wJEtVjsYL", result.referenceId)
        assertEquals("https://mbreciept.cbe.com.et/v2-hfHCxNXPQY6wJEtVjsYL", result.receiptUrl)
        assertEquals(fallbackTime, result.timestampMillis)
    }

    @Test
    fun `parses CBE transfer SMS with fees and disaster recovery`() {
        val sms = "Dear Ahmed Muhammed Seid You have successfully transferred ETB 370.00 from account 1**8265 to account 1**6816 (Wubshet Mulat Ertiban). Service charge of ETB 0.00 and VAT(15%) of ETB 0.00 and Disaster Recovery(5%) of 0.00 with total of ETB 370.00. Your current balance is ETB 15.28. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxO8qtIb9xFcXwEez  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6"
        val fallbackTime = 1763281000000L

        val result = parser.parse("CBE", sms, fallbackTime)
        assertNotNull(result)
        result!!

        assertEquals(TransactionType.OUTGOING, result.type)
        assertEquals(370_00L, result.amountMinor)
        assertEquals(0L, result.feeMinor)
        assertEquals(370_00L, result.totalAmountMinor)
        assertEquals(15_28L, result.balanceAfterMinor)
        assertEquals("CBE_1**8265", result.accountId)
        assertEquals("Wubshet Mulat Ertiban", result.counterparty)
        assertEquals("1**6816", result.counterpartyAccount)
        assertEquals("v2-hfHCxO8qtIb9xFcXwEez", result.referenceId)
    }

    @Test
    fun `parses CBE transfer with non-zero fees accurately`() {
        val sms = "Dear Ahmed Muhammed Seid You have successfully transferred ETB 1,000.00 from account 1**8265 to account 1**6816 (Wubshet Mulat Ertiban). Service charge of ETB 5.00 and VAT(15%) of ETB 0.75 and Disaster Recovery(5%) of 0.25 with total of ETB 1,006.00. Your current balance is ETB 4,666.28. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-abc123"

        val result = parser.parse("CBE", sms, 1000L)!!
        assertEquals(1000_00L, result.amountMinor)
        assertEquals(5_00L, result.serviceFeeMinor)
        assertEquals(75L, result.vatMinor)
        assertEquals(25L, result.extraFeeMinor)
        assertEquals(6_00L, result.feeMinor)
        assertEquals(1006_00L, result.totalAmountMinor)
    }
}
