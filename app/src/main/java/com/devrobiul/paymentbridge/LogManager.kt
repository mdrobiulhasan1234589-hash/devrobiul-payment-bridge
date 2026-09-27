package com.devrobiul.paymentbridge

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {

    private const val PREF_NAME = "devrobiul_payment_bridge_logs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 300
    private const val SEPARATOR = "\n---LOG---\n"

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US)

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun add(context: Context, tag: String, message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val entry = "[$timestamp] [$tag] $message"
            val existing = getPrefs(context).getString(KEY_LOGS, "") ?: ""
            val updated = if (existing.isEmpty()) entry else "$existing$SEPARATOR$entry"

            val parts = updated.split(SEPARATOR)
            val trimmed = if (parts.size > MAX_LOGS) {
                parts.takeLast(MAX_LOGS).joinToString(SEPARATOR)
            } else {
                updated
            }

            getPrefs(context).edit().putString(KEY_LOGS, trimmed).apply()
        } catch (_: Exception) { }
    }

    fun getAll(context: Context): String {
        return try {
            getPrefs(context).getString(KEY_LOGS, "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun getList(context: Context): List<String> {
        val raw = getAll(context)
        if (raw.isEmpty()) return emptyList()
        return raw.split(SEPARATOR).reversed()
    }

    fun clear(context: Context) {
        try {
            getPrefs(context).edit().remove(KEY_LOGS).apply()
        } catch (_: Exception) { }
    }
}
