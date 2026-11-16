package com.yourname.etbledger.ui.common

import com.yourname.etbledger.data.local.TransactionType
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

val StandardCategories = listOf(
    "Food & Groceries",
    "Transport",
    "Telecom & Data",
    "Bills & Utilities",
    "Shopping",
    "Transfers",
    "Fees",
    "Income & Transfers",
    "Salary",
    "Health",
    "Entertainment",
    "Uncategorized"
)

fun formatEtb(minor: Long, signedForType: TransactionType? = null): String {
    val nf = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val absText = nf.format(abs(minor) / 100.0)
    return when {
        signedForType == TransactionType.INCOME -> "+ETB $absText"
        signedForType == TransactionType.OUTGOING -> "-ETB $absText"
        minor < 0L -> "-ETB $absText"
        else -> "ETB $absText"
    }
}

private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm")
private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM dd")

fun formatDateTime(millis: Long): String {
    val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return dateTimeFormatter.format(zdt)
}

fun formatShortDate(millis: Long): String {
    val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return shortDateFormatter.format(zdt)
}
