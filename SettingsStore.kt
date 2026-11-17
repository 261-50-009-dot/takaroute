package com.takaroute.smscompanion

import android.content.Context
import java.net.URI
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.security.KeyStore
import javax.crypto.spec.GCMParameterSpec

/** Stores server token encrypted with Android Keystore; never logs or displays it. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("companion_settings", Context.MODE_PRIVATE)

    val apiBase: String get() = prefs.getString("api_base", "") ?: ""
    val allowedSenders: String get() = prefs.getString("allowed_senders", "bKash") ?: "bKash"
    val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    val lastTransaction: String? get() = prefs.getString("last_transaction", null)
    val lastAmountMinor: Long get() = prefs.getLong("last_amount_minor", 0L)
    val detectedCount: Int get() = prefs.getInt("detected_count", 0)
    val uploadConfigured: Boolean get() = apiBase.startsWith("https://") && hasToken()

    /** Do not notify/upload twice for the same SMS receipt; store only derived metadata. */
    @Synchronized
    fun recordReceipt(receipt: IncomingReceipt): Boolean {
        val stored = prefs.getString("seen_event_ids", "").orEmpty()
        val seen = stored.split(",").filter { it.isNotBlank() }
        if (seen.contains(receipt.eventId)) return false
        prefs.edit()
            .putString("seen_event_ids", (seen + receipt.eventId).takeLast(100).joinToString(","))
            .putString("last_transaction", receipt.transactionId)
            .putLong("last_amount_minor", receipt.amountMinor)
            .putInt("detected_count", detectedCount + 1)
            .apply()
        return true
    }

    val deviceId: String
        get() {
            prefs.getString("device_id", null)?.let { return it }
            val id = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
            return id
        }

    fun isAllowedSender(sender: String): Boolean = allowedSenders
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .any { sender.equals(it, ignoreCase = true) }

    fun saveConfig(baseUrl: String, senders: String, suppliedToken: String) {
        val cleaned = baseUrl.trim().trimEnd('/')
        // Local-only mode does not require a server. If a URL is supplied, upload credentials
        // become mandatory; no plaintext HTTP or embedded usernames/passwords.
        if (cleaned.isNotEmpty()) {
            val uri = URI(cleaned)
            require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
                    uri.userInfo == null && (uri.path.isNullOrBlank() || uri.path == "/") &&
                    uri.query == null && uri.fragment == null) {
                "Use HTTPS such as https://api.example.com (no path), or leave blank for local alerts."
            }
        }
        require(senders.isNotBlank() && senders.split(",").all {
            it.trim().matches(Regex("[A-Za-z0-9+ -]{2,30}"))
        }) { "Enter exact SMS sender IDs, separated by commas." }
        if (suppliedToken.isNotBlank()) {
            require(suppliedToken.trim().length >= 24) { "Use a device token of at least 24 characters." }
            val encrypted = encrypt(suppliedToken.trim())
            prefs.edit().putString("encrypted_token", encrypted).apply()
        }
        require(cleaned.isEmpty() || hasToken()) {
            "An API token is required when the HTTPS server is configured."
        }
        prefs.edit().putString("api_base", cleaned)
            .putString("allowed_senders", senders.trim())
            .apply()
    }

    fun setEnabled(value: Boolean) = prefs.edit().putBoolean("enabled", value).apply()

    fun hasToken(): Boolean = !prefs.getString("encrypted_token", null).isNullOrBlank()

    fun getToken(): String? {
        return try {
            val stored = prefs.getString("encrypted_token", null) ?: return null
            val parts = stored.split(":", limit = 2)
            if (parts.size != 2) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun encrypt(cleartext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipher.doFinal(cleartext.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$encrypted"
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        (store.getKey("takaroute_ingest_token", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                "takaroute_ingest_token",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
