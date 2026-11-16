package com.yourname.etbledger.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class RawSmsMessage(
    val sender: String,
    val body: String,
    val timestampMillis: Long
)

@Singleton
class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasReadSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

    suspend fun readInboxMessages(sinceMillis: Long): List<RawSmsMessage> = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext emptyList()

        val result = mutableListOf<RawSmsMessage>()
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = if (sinceMillis > 0L) "${Telephony.Sms.DATE} >= ?" else null
        val selectionArgs = if (sinceMillis > 0L) arrayOf(sinceMillis.toString()) else null

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressIdx)?.trim().orEmpty()
                val body = cursor.getString(bodyIdx).orEmpty()
                val date = cursor.getLong(dateIdx)
                if (sender.isNotBlank() && body.isNotBlank()) {
                    result.add(RawSmsMessage(sender = sender, body = body, timestampMillis = date))
                }
            }
        }

        result
    }
}
