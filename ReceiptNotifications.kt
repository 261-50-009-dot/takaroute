package com.takaroute.smscompanion

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
import java.util.Locale

/** A matched SMS is a possible receipt, never proof that an order is paid. */
object ReceiptNotifications {
    private const val CHANNEL_ID = "takaroute_receipts"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Possible bKash receipts", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts when a new allowed sender SMS matches a received-payment pattern" }
        manager.createNotificationChannel(channel)
    }

    fun showReceived(context: Context, receipt: IncomingReceipt) {
        val amount = String.format(Locale.US, "%,.2f", receipt.amountMinor / 100.0)
        show(context, "Possible bKash receipt", "BDT $amount · TrxID ${receipt.transactionId}", receipt.eventId.hashCode())
    }

    fun showDemo(context: Context) {
        show(context, "TakaRoute test notification", "Demo: possible BDT 1,800.00 receipt (not a verified payment)", 7122026)
    }

    private fun show(context: Context, title: String, detail: String, id: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val openIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val publicRedaction = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("TakaRoute SMS Companion")
            .setContentText("A possible payment SMS was detected")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\nCheck your wallet to verify payment. SMS alone is not proof."))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicRedaction)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) { /* User revoked notification permission. */ }
    }
}
