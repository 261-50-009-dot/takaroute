package com.takaroute.smscompanion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var settings: SettingsStore
    private lateinit var status: TextView
    private lateinit var url: EditText
    private lateinit var token: EditText
    private lateinit var senders: EditText
    private lateinit var consent: CheckBox
    private lateinit var lastReceipt: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val allowed = hasPermission(Manifest.permission.RECEIVE_SMS)
        settings.setEnabled(allowed && consent.isChecked)
        showStatus(if (allowed && consent.isChecked) {
            if (canNotify()) "Monitoring on. Matching bKash SMS will show notifications."
            else "SMS monitoring on, but notifications are blocked. Enable Notifications in Android Settings."
        } else "SMS permission denied or consent withdrawn. Monitoring disabled.")
        refreshSummary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = SettingsStore(this)
        ReceiptNotifications.createChannel(this)
        status = findViewById(R.id.status)
        url = findViewById(R.id.apiUrl)
        token = findViewById(R.id.apiToken)
        senders = findViewById(R.id.senders)
        consent = findViewById(R.id.consent)
        lastReceipt = findViewById(R.id.lastReceipt)
        url.setText(settings.apiBase)
        senders.setText(settings.allowedSenders)
        token.hint = if (settings.hasToken()) "Saved securely; leave blank to keep" else "Optional for local alerts"
        consent.isChecked = settings.enabled
        showStatus(if (settings.enabled) "Monitoring enabled. SMS is NOT proof of payment."
                   else "Monitoring disabled. Enable to detect new bKash SMS.")
        refreshSummary()

        findViewById<Button>(R.id.enableButton).setOnClickListener {
            if (!consent.isChecked) {
                showStatus("Read and accept the consent statement first.")
                return@setOnClickListener
            }
            try {
                settings.setEnabled(false)
                settings.saveConfig(url.text.toString(), senders.text.toString(), token.text.toString())
                token.text.clear()
                val permissions = mutableListOf<String>()
                if (!hasPermission(Manifest.permission.RECEIVE_SMS)) permissions.add(Manifest.permission.RECEIVE_SMS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canNotify())
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                if (permissions.isNotEmpty()) {
                    showStatus("Grant SMS and notification permissions when asked.")
                    permissionLauncher.launch(permissions.toTypedArray())
                } else {
                    settings.setEnabled(true)
                    showStatus("Monitoring enabled. Notification alerts active. ${if (settings.uploadConfigured) "Server sync on." else "Local-only mode."}")
                }
                refreshSummary()
            } catch (e: Exception) {
                showStatus(e.message ?: "Invalid settings.")
            }
        }
        findViewById<Button>(R.id.disableButton).setOnClickListener {
            settings.setEnabled(false)
            consent.isChecked = false
            showStatus("Monitoring disabled. Incoming SMS will be ignored.")
            refreshSummary()
        }
        findViewById<Button>(R.id.demoButton).setOnClickListener {
            val example = "You have received Tk 1,800.00 from 01700000000. TrxID ABC1234567 at 10:00."
            val parsed = BkashSmsParser.parse("bKash", example)
            showStatus(if (parsed == null) "Sample not recognized."
                else "Offline parser test: TrxID ${parsed.transactionId}, BDT ${formatAmount(parsed.amountMinor)}. Nothing uploaded.")
        }
        findViewById<Button>(R.id.notifyButton).setOnClickListener {
            if (!canNotify() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                showStatus("Allow notification permission, then tap test again.")
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            } else {
                ReceiptNotifications.showDemo(this)
                showStatus("Test notification sent to the Android notification shade (demo only).")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::settings.isInitialized && ::lastReceipt.isInitialized) refreshSummary()
    }

    private fun refreshSummary() {
        val id = settings.lastTransaction
        lastReceipt.text = if (id == null) "No matching SMS detected yet."
        else "Detected: ${settings.detectedCount}\nLast possible receipt: BDT ${formatAmount(settings.lastAmountMinor)}\nTrxID: $id\nStatus: unverified SMS (check your wallet)"
    }

    private fun formatAmount(amountMinor: Long): String =
        String.format(Locale.US, "%,.2f", amountMinor / 100.0)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun canNotify(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun showStatus(text: String) { status.text = text }
}
