package com.takaroute.smscompanion

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) return

        val settings = SettingsStore(context)
        if (!settings.enabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.first().displayOriginatingAddress ?: return
        if (!settings.isAllowedSender(sender)) return
        if (messages.any { !sender.equals(it.displayOriginatingAddress, ignoreCase = true) }) return

        // The receiver only sees newly arriving messages; it never reads the SMS inbox.
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val receipt = BkashSmsParser.parse(sender, body) ?: return
        if (!settings.recordReceipt(receipt)) return
        // Local notification is independent of Internet access or any external server.
        ReceiptNotifications.showReceived(context, receipt)
        if (!settings.uploadConfigured) return
        val json = JSONObject().apply {
            put("event_id", receipt.eventId)
            put("device_id", settings.deviceId)
            put("provider", "bkash_sms")
            put("sms_sender", sender)
            put("transaction_id", receipt.transactionId)
            put("amount_minor", receipt.amountMinor)
            put("currency", "BDT")
            put("observed_at", Instant.now().toString())
        }
        // No raw message, PIN, OTP, account balance or phone number is uploaded.
        val work = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.PAYLOAD_KEY to json.toString()))
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "receipt-${receipt.eventId}", ExistingWorkPolicy.KEEP, work
        )
    }
}
