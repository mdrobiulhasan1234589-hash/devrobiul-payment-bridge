package com.devrobiul.paymentbridge

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val recentTrxIds = LinkedHashMap<String, Long>()
    private val TRX_MEMORY_WINDOW_MS = 10 * 60 * 1000L
    private val MAX_RECENT = 100

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            LogManager.add(applicationContext, "INFO", "🔌 NotificationListener CONNECTED")
        } catch (_: Exception) { }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            LogManager.add(applicationContext, "INFO", "🔌 NotificationListener DISCONNECTED")
        } catch (_: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            val packageName = sbn.packageName ?: return

            // Only process notifications from payment provider apps
            if (!isPaymentSourcePackage(packageName)) return

            if (!Prefs.isMonitoringEnabled(applicationContext)) {
                LogManager.add(applicationContext, "DEBUG", "⚠️ Monitoring disabled")
                return
            }

            LogManager.add(applicationContext, "DEBUG", "📩 Notification from: $packageName")

            val extras = sbn.notification?.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString(" ") { it.toString() } ?: ""

            val combinedText = listOf(text, bigText, textLines)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { text }

            LogManager.add(
                applicationContext,
                "DEBUG",
                "📝 Title: $title | Text: ${combinedText.take(120)}"
            )

            if (combinedText.isBlank() && title.isBlank()) return

            val payment = PaymentParser.parse(title, combinedText) { logMsg ->
                LogManager.add(applicationContext, "DEBUG", logMsg)
            }

            if (payment == null) {
                LogManager.add(applicationContext, "DEBUG", "❌ Parser returned NULL")
                return
            }

            val now = System.currentTimeMillis()
            pruneOldTrxIds(now)

            synchronized(recentTrxIds) {
                val lastSeen = recentTrxIds[payment.trxId]
                if (lastSeen != null && (now - lastSeen) < TRX_MEMORY_WINDOW_MS) {
                    LogManager.add(
                        applicationContext,
                        "DUPLICATE",
                        "Ignored duplicate: ${PaymentParser.maskTrxId(payment.trxId)}"
                    )
                    return
                }
                recentTrxIds[payment.trxId] = now
            }

            LogManager.add(
                applicationContext,
                "PARSED",
                "${payment.method.uppercase()} ৳${payment.amount} from " +
                        "${PaymentParser.maskPhone(payment.senderPhone)} " +
                        "TrxID ${PaymentParser.maskTrxId(payment.trxId)}"
            )

            serviceScope.launch {
                val success = FirebaseHelper.pushPaymentRecord(applicationContext, payment)

                if (success) {
                    val summary = buildSummary(payment)
                    Prefs.setLastPaymentSummary(applicationContext, summary)
                    LogManager.add(
                        applicationContext,
                        "SENT",
                        "Firebase push OK — ${PaymentParser.maskTrxId(payment.trxId)}"
                    )
                } else {
                    LogManager.add(
                        applicationContext,
                        "ERROR",
                        "Firebase push FAILED — ${PaymentParser.maskTrxId(payment.trxId)}"
                    )
                }
            }

        } catch (e: Exception) {
            try {
                LogManager.add(applicationContext, "ERROR", "Listener error: ${e.message}")
            } catch (_: Exception) { }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { }

    private fun isPaymentSourcePackage(pkg: String): Boolean {
        return when (pkg) {
            // bKash
            "com.bKash.customerapp" -> true
            "com.bkash.customerapp" -> true

            // Nagad
            "com.konasl.nagad" -> true
            "com.nagad.app" -> true

            // Rocket
            "com.dbbl.mbs.apps.rocket" -> true
            "com.dbbl.mbs" -> true

            else -> false
        }
    }

    private fun pruneOldTrxIds(now: Long) {
        synchronized(recentTrxIds) {
            val iterator = recentTrxIds.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if ((now - entry.value) > TRX_MEMORY_WINDOW_MS) {
                    iterator.remove()
                }
            }
            while (recentTrxIds.size > MAX_RECENT) {
                val firstKey = recentTrxIds.keys.firstOrNull() ?: break
                recentTrxIds.remove(firstKey)
            }
        }
    }

    private fun buildSummary(payment: PaymentParser.PaymentData): String {
        return try {
            val timeFormat = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.US)
            val maskedPhone = PaymentParser.maskPhone(payment.senderPhone)
            val maskedTrx = PaymentParser.maskTrxId(payment.trxId)
            "PUSHED|${payment.method}|${payment.amount}|$maskedPhone|$maskedTrx|${timeFormat.format(java.util.Date())}"
        } catch (_: Exception) {
            ""
        }
    }
}
