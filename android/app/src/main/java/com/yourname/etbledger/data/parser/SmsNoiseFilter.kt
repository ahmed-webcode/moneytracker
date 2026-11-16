package com.yourname.etbledger.data.parser

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsNoiseFilter @Inject constructor() {

    private val trackedSenderKeywords = listOf(
        "cbe",
        "telebirr",
        "127",
        "ethiotelecom",
        "awash",
        "dashen",
        "abyssinia",
        "boa"
    )

    private val otpPatterns = listOf(
        Regex("\\botp\\b", RegexOption.IGNORE_CASE),
        Regex("one[- ]time\\s+(?:password|pin|code)", RegexOption.IGNORE_CASE),
        Regex("verification\\s+code", RegexOption.IGNORE_CASE),
        Regex("security\\s+code", RegexOption.IGNORE_CASE),
        Regex("do not share (?:this )?(?:code|pin|otp)", RegexOption.IGNORE_CASE)
    )

    private val loginAlertPatterns = listOf(
        Regex("\\blogged\\s+in\\b", RegexOption.IGNORE_CASE),
        Regex("\\blogin\\s+(?:alert|attempt|notification)\\b", RegexOption.IGNORE_CASE),
        Regex("new\\s+device\\s+login", RegexOption.IGNORE_CASE)
    )

    private val failedPatterns = listOf(
        Regex("\\bfailed\\b", RegexOption.IGNORE_CASE),
        Regex("\\bunsuccessful\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdeclined\\b", RegexOption.IGNORE_CASE),
        Regex("\\binsufficient\\s+(?:balance|funds)\\b", RegexOption.IGNORE_CASE)
    )

    private val marketingPatterns = listOf(
        Regex("\\bwin\\s+(?:up to|birr|etb|prizes)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpromo(?:tion)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\bspecial\\s+offer\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdial\\s+\\*\\d+", RegexOption.IGNORE_CASE)
    )

    fun isTrackedFinancialSender(sender: String, body: String): Boolean {
        val normalizedSender = sender.trim().lowercase()
        if (trackedSenderKeywords.any { normalizedSender.contains(it) }) return true

        val normalizedBody = body.lowercase()
        return normalizedBody.contains("banking with cbe") ||
            normalizedBody.contains("using telebirr") ||
            normalizedBody.contains("mbreciept.cbe.com.et") ||
            normalizedBody.contains("mbreceipt.cbe.com.et") ||
            normalizedBody.contains("transactioninfo.ethiotelecom.et")
    }

    fun getIgnoreReason(body: String): String? {
        if (otpPatterns.any { it.containsMatchIn(body) }) return "OTP / Verification code"
        if (loginAlertPatterns.any { it.containsMatchIn(body) }) return "Login alert"
        if (failedPatterns.any { it.containsMatchIn(body) }) return "Failed / declined transaction"
        if (marketingPatterns.any { it.containsMatchIn(body) }) return "Marketing / promo message"
        return null
    }
}
