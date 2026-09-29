package com.devrobiul.paymentbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")

        for (pdu in pdus) {
            val message: SmsMessage? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }
            } catch (_: Exception) {
                null
            }

            if (message != null) {
                val sender = message.originatingAddress ?: ""
                val body = message.messageBody ?: ""

                LogManager.add(context, "SMS", "From: $sender | Body: ${body.take(80)}")

                // ✅ সাথে সাথে Firebase-এ push (Background Thread)
                scope.launch {
                    try {
                        SmsParser.processAndPush(context, sender, body)
                    } catch (e: Exception) {
                        LogManager.add(context, "ERROR", "Push failed: ${e.message}")
                    }
                }
            }
        }
    }
}
