package com.yourname.etbledger.data.parser

import com.yourname.etbledger.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TelebirrSmsParserTest {

    private val zone = ZoneId.of("Africa/Addis_Ababa")
    private val parser = TelebirrSmsParser(zone)

    @Test
    fun `parses telebirr receive from CBE accurately`() {
        val sms = """
            Dear Ahmed,
            You have received  ETB 100.00 by transaction number DKG88V6UT4 on 2026-11-16 08:31:59 from Commercial Bank of Ethiopia to your telebirr Account 251920928989 - Ahmed Muhammed Seid. Your current balance is ETB 110.86.
            Thank you for using telebirr
            Ethio telecom
        """.trimIndent()

        val result = parser.parse("telebirr", sms, 0L)
        assertNotNull(result)
        result!!

        assertEquals(TransactionType.INCOME, result.type)
        assertEquals(100_00L, result.amountMinor)
        assertEquals(0L, result.feeMinor)
        assertEquals(110_86L, result.balanceAfterMinor)
        assertEquals("DKG88V6UT4", result.referenceId)
        assertEquals("Commercial Bank of Ethiopia", result.counterparty)
        assertEquals("251920928989", result.accountIdentifier)
        assertEquals(TelebirrSmsParser.CANONICAL_ACCOUNT_ID, result.accountId)
        assertTrue(result.suggestedSelfTransfer)

        val expectedMillis = LocalDateTime.of(2026, 11, 16, 8, 31, 59)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedMillis, result.timestampMillis)
    }

    @Test
    fun `parses telebirr P2P transfer with service fee and VAT`() {
        val sms = """
            Dear Ahmed 
            You have transferred ETB 60.00 to Abdulmejid Yirga (2519****5217) on 14/11/2026 15:36:52. Your transaction number is DKE46N4RSQ. The service fee is  ETB 0.87 and  15% VAT on the service fee is ETB 0.13. Your current E-Money Account  balance is ETB 147.86. To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DKE46N4RSQ.

            Thank you for using telebirr
            Ethio telecom
        """.trimIndent()

        val result = parser.parse("127", sms, 0L)
        assertNotNull(result)
        result!!

        assertEquals(TransactionType.OUTGOING, result.type)
        assertEquals(60_00L, result.amountMinor)
        assertEquals(87L, result.serviceFeeMinor)
        assertEquals(13L, result.vatMinor)
        assertEquals(1_00L, result.feeMinor)
        assertEquals(61_00L, result.totalAmountMinor)
        assertEquals(147_86L, result.balanceAfterMinor)
        assertEquals("Abdulmejid Yirga", result.counterparty)
        assertEquals("2519****5217", result.counterpartyAccount)
        assertEquals("DKE46N4RSQ", result.referenceId)
        assertEquals(
            "https://transactioninfo.ethiotelecom.et/receipt/DKE46N4RSQ",
            result.receiptUrl
        )
        assertEquals(TelebirrSmsParser.CANONICAL_ACCOUNT_ID, result.accountId)
    }

    @Test
    fun `parses telebirr internet package purchase`() {
        val sms = """
            Dear Ahmed
            You have paid ETB 37.00 for package Internet Daily  Package 1.2GB purchase made for 920928989 on 15/11/2026 06:14:16. Your transaction number is  DKF67EJ2AY. Your current balance is ETB 73.86.To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DKF67EJ2AY
            Thank you for using telebirr
            Ethio telecom
        """.trimIndent()

        val result = parser.parse("telebirr", sms, 0L)
        assertNotNull(result)
        result!!

        assertEquals(TransactionType.OUTGOING, result.type)
        assertEquals(37_00L, result.amountMinor)
        assertEquals(37_00L, result.totalAmountMinor)
        assertEquals(73_86L, result.balanceAfterMinor)
        assertEquals("Internet Daily Package 1.2GB", result.counterparty)
        assertEquals("251920928989", result.accountIdentifier)
        assertEquals("DKF67EJ2AY", result.referenceId)
        assertEquals(
            "https://transactioninfo.ethiotelecom.et/receipt/DKF67EJ2AY",
            result.receiptUrl
        )
    }

    @Test
    fun `returns null for unrecognized telebirr message`() {
        val sms = "Dear customer, enjoy 50% bonus on your next recharge with telebirr."
        assertNull(parser.parse("telebirr", sms, 0L))
    }
}
