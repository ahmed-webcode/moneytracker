package com.yourname.etbledger.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.yourname.etbledger.data.repository.LedgerRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var ledgerRepository: LedgerRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Reassemble multi-part PDUs belonging to the same sender
        val groupedBodies = linkedMapOf<String, Pair<StringBuilder, Long>>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "UNKNOWN"
            val bodyPart = sms.displayMessageBody ?: sms.messageBody ?: ""
            val timestamp = sms.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
            val entry = groupedBodies.getOrPut(sender) { StringBuilder() to timestamp }
            entry.first.append(bodyPart)
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for ((sender, pair) in groupedBodies) {
                    val fullBody = pair.first.toString()
                    if (fullBody.isNotBlank()) {
                        ledgerRepository.ingestRealtimeSms(
                            sender = sender,
                            body = fullBody,
                            receivedAtMillis = pair.second
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
