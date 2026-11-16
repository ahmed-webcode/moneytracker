package com.yourname.etbledger.data.parser

import java.math.BigDecimal
import java.security.MessageDigest

object MoneyUtils {

    fun parseEtbToMinor(rawAmount: String): Long {
        val cleaned = rawAmount.replace(",", "").trim()
        return BigDecimal(cleaned)
            .movePointRight(2)
            .longValueExact()
    }

    fun tryParseEtbToMinor(rawAmount: String): Long? =
        runCatching { parseEtbToMinor(rawAmount) }.getOrNull()

    fun normalizeSpaces(input: String): String =
        input.trim().replace(Regex("\\s+"), " ")

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun buildSmsDedupeKey(
        institution: String,
        referenceId: String?,
        sender: String,
        body: String
    ): String {
        return if (!referenceId.isNullOrBlank()) {
            "REF:${institution.uppercase()}:${referenceId.trim()}"
        } else {
            val normalized = "${sender.trim().lowercase()}|${normalizeSpaces(body)}"
            "SMS:${sha256(normalized)}"
        }
    }

    fun buildUnparsedDedupeHash(sender: String, body: String): String {
        val normalized = "${sender.trim().lowercase()}|${normalizeSpaces(body)}"
        return sha256(normalized)
    }
}
