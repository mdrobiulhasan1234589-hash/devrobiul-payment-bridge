package com.devrobiul.paymentbridge

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    private const val PREF_NAME = "devrobiul_payment_bridge_prefs"

    private const val KEY_FIREBASE_API_KEY = "firebase_api_key"
    private const val KEY_FIREBASE_DB_URL = "firebase_db_url"
    private const val KEY_FIREBASE_PROJECT_ID = "firebase_project_id"
    private const val KEY_FIREBASE_APP_ID = "firebase_app_id"
    private const val KEY_FIREBASE_SENDER_ID = "firebase_sender_id"
    private const val KEY_DATA_PATH = "data_path"
    private const val KEY_MONITORING = "monitoring_enabled"
    private const val KEY_LAST_PAYMENT = "last_payment_summary"
    private const val KEY_FIREBASE_CONNECTED = "firebase_connected"
    private const val KEY_FIREBASE_LAST_CHECK = "firebase_last_check"

    private fun get(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getFirebaseApiKey(context: Context): String =
        get(context).getString(KEY_FIREBASE_API_KEY, "") ?: ""

    fun setFirebaseApiKey(context: Context, value: String) {
        get(context).edit().putString(KEY_FIREBASE_API_KEY, value.trim()).apply()
    }

    fun getFirebaseDbUrl(context: Context): String =
        get(context).getString(KEY_FIREBASE_DB_URL, "") ?: ""

    fun setFirebaseDbUrl(context: Context, value: String) {
        get(context).edit().putString(KEY_FIREBASE_DB_URL, value.trim()).apply()
    }

    fun getFirebaseProjectId(context: Context): String =
        get(context).getString(KEY_FIREBASE_PROJECT_ID, "") ?: ""

    fun setFirebaseProjectId(context: Context, value: String) {
        get(context).edit().putString(KEY_FIREBASE_PROJECT_ID, value.trim()).apply()
    }

    fun getFirebaseAppId(context: Context): String =
        get(context).getString(KEY_FIREBASE_APP_ID, "") ?: ""

    fun setFirebaseAppId(context: Context, value: String) {
        get(context).edit().putString(KEY_FIREBASE_APP_ID, value.trim()).apply()
    }

    fun getFirebaseSenderId(context: Context): String =
        get(context).getString(KEY_FIREBASE_SENDER_ID, "") ?: ""

    fun setFirebaseSenderId(context: Context, value: String) {
        get(context).edit().putString(KEY_FIREBASE_SENDER_ID, value.trim()).apply()
    }

    fun getDataPath(context: Context): String =
        get(context).getString(KEY_DATA_PATH, "XNXANIKPAY") ?: "XNXANIKPAY"

    fun setDataPath(context: Context, value: String) {
        get(context).edit().putString(KEY_DATA_PATH, value.trim()).apply()
    }

    fun isMonitoringEnabled(context: Context): Boolean =
        get(context).getBoolean(KEY_MONITORING, true)

    fun setMonitoringEnabled(context: Context, value: Boolean) {
        get(context).edit().putBoolean(KEY_MONITORING, value).apply()
    }

    fun getLastPaymentSummary(context: Context): String =
        get(context).getString(KEY_LAST_PAYMENT, "") ?: ""

    fun setLastPaymentSummary(context: Context, value: String) {
        get(context).edit().putString(KEY_LAST_PAYMENT, value).apply()
    }

    fun isFirebaseConnected(context: Context): Boolean =
        get(context).getBoolean(KEY_FIREBASE_CONNECTED, false)

    fun setFirebaseConnected(context: Context, value: Boolean) {
        get(context).edit().putBoolean(KEY_FIREBASE_CONNECTED, value).apply()
    }

    fun getFirebaseLastCheck(context: Context): Long =
        get(context).getLong(KEY_FIREBASE_LAST_CHECK, 0L)

    fun setFirebaseLastCheck(context: Context, value: Long) {
        get(context).edit().putLong(KEY_FIREBASE_LAST_CHECK, value).apply()
    }

    fun isFirebaseConfigured(context: Context): Boolean {
        return getFirebaseApiKey(context).isNotBlank() &&
                getFirebaseDbUrl(context).isNotBlank() &&
                getFirebaseAppId(context).isNotBlank()
    }

    fun clearFirebaseConfig(context: Context) {
        get(context).edit()
            .remove(KEY_FIREBASE_API_KEY)
            .remove(KEY_FIREBASE_DB_URL)
            .remove(KEY_FIREBASE_PROJECT_ID)
            .remove(KEY_FIREBASE_APP_ID)
            .remove(KEY_FIREBASE_SENDER_ID)
            .remove(KEY_FIREBASE_CONNECTED)
            .remove(KEY_FIREBASE_LAST_CHECK)
            .apply()
    }
}
