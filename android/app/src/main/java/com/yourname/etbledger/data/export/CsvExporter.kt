package com.yourname.etbledger.data.export

import android.content.Context
import android.net.Uri
import com.yourname.etbledger.data.local.entity.AccountEntity
import com.yourname.etbledger.data.local.entity.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    suspend fun exportTransactionsToUri(
        uri: Uri,
        transactions: List<TransactionEntity>,
        accountsById: Map<String, AccountEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val zone = ZoneId.systemDefault()
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.appendLine(
                        "Date,Time,Account,Type,Category,Counterparty,Counterparty_Account,Amount_ETB,Fee_ETB,Total_ETB,Balance_After_ETB,Self_Transfer,Reference_ID,Source,Note"
                    )
                    for (tx in transactions) {
                        val zdt = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone)
                        val accountLabel = accountsById[tx.accountId]?.displayName ?: tx.accountId
                        val row = listOf(
                            dateFormatter.format(zdt),
                            timeFormatter.format(zdt),
                            accountLabel,
                            tx.type.name,
                            tx.category,
                            tx.counterparty.orEmpty(),
                            tx.counterpartyAccount.orEmpty(),
                            minorToDecimalString(tx.amountMinor),
                            minorToDecimalString(tx.feeMinor),
                            minorToDecimalString(tx.totalAmountMinor),
                            tx.balanceAfterMinor?.let(::minorToDecimalString).orEmpty(),
                            tx.isSelfTransfer.toString(),
                            tx.referenceId.orEmpty(),
                            tx.source.name,
                            tx.note.orEmpty()
                        ).joinToString(",") { escapeCsv(it) }
                        writer.appendLine(row)
                    }
                    writer.flush()
                }
            } ?: error("Unable to open output stream for selected file")
            transactions.size
        }
    }

    private fun minorToDecimalString(minor: Long): String =
        BigDecimal.valueOf(minor, 2).toPlainString()

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
