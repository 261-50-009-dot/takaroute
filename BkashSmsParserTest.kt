package com.takaroute.smscompanion

import org.junit.Assert.*
import org.junit.Test

class BkashSmsParserTest {
    @Test fun recognizesIncomingReceipt() {
        val msg = "You have received Tk 1,800.00 from 01700000000. TrxID ABC1234567 at 10:00."
        val receipt = BkashSmsParser.parse("bKash", msg)
        assertNotNull(receipt)
        assertEquals(180000L, receipt!!.amountMinor)
        assertEquals("ABC1234567", receipt.transactionId)
        assertEquals(64, receipt.eventId.length)
    }

    @Test fun ignoresOutgoingPayment() {
        assertNull(BkashSmsParser.parse("bKash", "You have sent Tk 500 to 01700000000. TrxID ABC1234567"))
    }

    @Test fun ignoresOtp() {
        assertNull(BkashSmsParser.parse("bKash", "OTP 123456. You have received Tk 100.00. TrxID ABC1234567"))
    }

    @Test fun ignoresReceiptWithoutTrxId() {
        assertNull(BkashSmsParser.parse("bKash", "You have received Tk 100.00."))
    }

    @Test fun idempotentEventId() {
        val msg = "Received Tk 250.00. TrxID X123456789"
        val a = BkashSmsParser.parse("bKash", msg)
        val b = BkashSmsParser.parse("bKash", msg)
        assertNotNull(a)
        assertEquals(a!!.eventId, b!!.eventId)
    }

    @Test fun refusesNegativeOrZeroAmounts() {
        assertNull(BkashSmsParser.parse("bKash", "Received Tk 0.00. TrxID X123456789"))
    }
}
