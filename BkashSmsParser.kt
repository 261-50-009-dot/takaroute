package com.takaroute.smscompanion

import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Conservative, offline parser for an incoming bKash-style receipt notification.
 * The actual message templates vary. Unrecognized notifications are ignored.
 * Sender IDs and SMS bodies are NOT authenticated proof of money received.
 */
data class IncomingReceipt(
    val transactionId: String,
    val amountMinor: Long,
    val eventId: String
)

object BkashSmsParser {
    private val prohibited = Regex(
        """(?i)\b(?:OTP|PIN|password|verification\s+code|one[ -]time\s+(?:password|code)|login\s+code)\b"""
    )
    private val incomingAmount = Regex(
        """(?i)\b(?:you\s+have\s+)?received\s+(?:tk\.?|bdt)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b"""
    )
    private val trx = Regex(
        """(?i)\b(?:Trx\s*ID|TrxID|Transaction\s*ID)\s*[:#-]?\s*([A-Za-z0-9]{6,30})\b"""
    )

    fun parse(sender: String, body: String): IncomingReceipt? {
        if (body.length > 3000 || prohibited.containsMatchIn(body)) return null
        val amountText = incomingAmount.find(body)?.groupValues?.get(1) ?: return null
        val transactionId = trx.find(body)?.groupValues?.get(1)?.uppercase() ?: return null
        val amountMinor = try {
            BigDecimal(amountText.replace(",", ""))
                .movePointRight(2)
                .longValueExact()
        } catch (_: Exception) {
            return null
        }
        if (amountMinor <= 0 || amountMinor > 100_000_000_00L) return null
        val id = sha256("${sender.lowercase()}|$transactionId|$amountMinor")
        return IncomingReceipt(transactionId, amountMinor, id)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
