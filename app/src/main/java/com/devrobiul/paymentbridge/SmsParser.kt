package com.devrobiul.paymentbridge

import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsParser {

    fun processAndPush(context: Context, sender: String, body: String) {
        // SMS থেকে ডাটা বের করি
        val amount = extractAmount(body) ?: return
        val trxId = extractTrxId(body) ?: return
        val senderNumber = extractSenderNumber(body) ?: ""
        val method = detectMethod(sender, body)

        // যদি কোনো bkash/nagad/rocket না হয়, skip করি
        if (method == "UNKNOWN") return

        // Firebase-এ push করি
        pushToFirebase(context, method, amount, senderNumber, trxId, body)
    }

    private fun extractAmount(body: String): String? {
        // "Tk 500" বা "Tk 1,500.00" বা "BDT 500" ধরার regex
        val patterns = listOf(
            Regex("""(?:Tk|BDT|tk)\s*([0-9,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9,]+(?:\.\d+)?)\s*(?:Tk|BDT)""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(body) ?: continue
            val amt = m.groupValues[1].replace(",", "")
            if (amt.isNotBlank()) return amt
        }
        return null
    }

    private fun extractTrxId(body: String): String? {
        // "TrxID: ABC123" বা "TxnID: XYZ" বা "Transaction ID: 12345"
        val patterns = listOf(
            Regex("""(?:TrxID|TxnID|Transaction\s*ID|Trx\s*ID)[\s:]+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE),
            Regex("""\b([A-Z0-9]{8,12})\b""") // Fallback — বড় হাতের ৮-১২ ডিজিট
        )
        for (p in patterns) {
            val m = p.find(body) ?: continue
            return m.groupValues[1]
        }
        return null
    }

    private fun extractSenderNumber(body: String): String? {
        // "01XXXXXXXXX" বা "+8801XXXXXXXXX"
        val p = Regex("""(?:\+?880|0)1[3-9]\d{8}""")
        return p.find(body)?.value
    }

    private fun detectMethod(sender: String, body: String): String {
        val combined = "$sender $body".uppercase()
        return when {
            combined.contains("BKASH") -> "BKASH"
            combined.contains("NAGAD") -> "NAGAD"
            combined.contains("ROCKET") -> "ROCKET"
            else -> "UNKNOWN"
        }
    }

    private fun pushToFirebase(
        context: Context,
        method: String,
        amount: String,
        senderNumber: String,
        trxId: String,
        rawBody: String
    ) {
        val dbUrl = Prefs.getFirebaseDbUrl(context)
        val dataPath = Prefs.getDataPath(context).ifBlank { "XNXANIKPAY" }

        if (dbUrl.isBlank()) {
            LogManager.add(context, "ERROR", "Firebase DB URL not set")
            return
        }

        try {
            val db = FirebaseDatabase.getInstance(dbUrl).reference
            val paymentRef = db.child(dataPath).push()
            val time = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())

            val data = mapOf(
                "method" to method,
                "amount" to amount,
                "senderNumber" to senderNumber,
                "trxId" to trxId,
                "rawBody" to rawBody,
                "time" to time,
                "timestamp" to System.currentTimeMillis(),
                "status" to "PENDING"
            )

            paymentRef.setValue(data)
                .addOnSuccessListener {
                    LogManager.add(context, "PUSH", "✓ Sent: $method ৳$amount TrxID: $trxId")
                    saveLastPayment(context, method, amount, senderNumber, trxId, time)
                }
                .addOnFailureListener { e ->
                    LogManager.add(context, "ERROR", "✗ Firebase push failed: ${e.message}")
                }
        } catch (e: Exception) {
            LogManager.add(context, "ERROR", "Firebase error: ${e.message}")
        }
    }

    private fun saveLastPayment(
        context: Context,
        method: String,
        amount: String,
        senderNumber: String,
        trxId: String,
        time: String
    ) {
        val summary = "$method|$amount|$senderNumber|$trxId|$time"
        Prefs.setLastPaymentSummary(context, summary)
    }
}
