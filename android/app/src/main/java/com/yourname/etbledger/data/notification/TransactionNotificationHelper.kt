package com.yourname.etbledger.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yourname.etbledger.MainActivity
import com.yourname.etbledger.R
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.entity.TransactionEntity
import com.yourname.etbledger.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences
) {
    private val channelId = "etb_transactions_channel"

    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Bank & Wallet Transactions",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Instant alerts when CBE or telebirr SMS transactions are logged"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyNewTransaction(tx: TransactionEntity, monthlyOutgoingMinor: Long? = null) {
        if (!preferences.settings.value.alertsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val amountStr = formatEtb(
            if (tx.type == TransactionType.OUTGOING) tx.totalAmountMinor else tx.amountMinor
        )
        val prefix = if (tx.type == TransactionType.INCOME) "Received" else "Spent"
        val title = "$prefix $amountStr • ${tx.category}"
        val details = buildString {
            tx.counterparty?.let { append(it) }
            tx.balanceAfterMinor?.let {
                if (isNotEmpty()) append(" • ")
                append("Bal: ${formatEtb(it)}")
            }
        }.ifBlank { "Logged from SMS" }

        val budgetLimit = preferences.settings.value.monthlyOutgoingBudgetMinor
        val overBudgetNote = if (
            budgetLimit > 0L &&
            monthlyOutgoingMinor != null &&
            monthlyOutgoingMinor > budgetLimit
        ) {
            "\nBudget alert: Monthly outgoing (${formatEtb(monthlyOutgoingMinor)}) exceeded ${formatEtb(budgetLimit)}"
        } else {
            ""
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            tx.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details + overBudgetNote))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(
            (tx.id.takeIf { it > 0 } ?: System.currentTimeMillis()).toInt(),
            notification
        )
    }

    private fun formatEtb(minor: Long): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "ETB ${nf.format(minor / 100.0)}"
    }
}
