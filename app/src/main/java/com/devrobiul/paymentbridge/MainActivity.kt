package com.devrobiul.paymentbridge

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var tvServiceStatus: TextView
    private lateinit var tvAccessStatus: TextView
    private lateinit var tvFirebaseStatus: TextView
    private lateinit var tvLastPayment: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnOpenAccess: Button
    private lateinit var btnTestConnection: Button
    private lateinit var btnSettings: Button
    private lateinit var btnLogs: Button
    private lateinit var btnHistory: Button

    private val NOTIF_PERMISSION_CODE = 201
    private val SMS_PERMISSION_CODE = 202
    private var lastTestTime = 0L
    private val TEST_CACHE_MS = 60_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupListeners()
        requestNotificationPermissionIfNeeded()
        requestSmsPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()

        if (Prefs.isMonitoringEnabled(this)) {
            PaymentMonitorService.start(this)
        }

        val now = System.currentTimeMillis()
        if (Prefs.isFirebaseConfigured(this) && (now - lastTestTime) > TEST_CACHE_MS) {
            lastTestTime = now
            runFirebaseTestSilent()
        }
    }

    private fun bindViews() {
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvAccessStatus = findViewById(R.id.tvAccessStatus)
        tvFirebaseStatus = findViewById(R.id.tvFirebaseStatus)
        tvLastPayment = findViewById(R.id.tvLastPayment)
        btnStart = findViewById(R.id.btnStartMonitoring)
        btnStop = findViewById(R.id.btnStopMonitoring)
        btnOpenAccess = findViewById(R.id.btnOpenAccess)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        btnSettings = findViewById(R.id.btnSettings)
        btnLogs = findViewById(R.id.btnLogs)
        btnHistory = findViewById(R.id.btnHistory)
    }

    private fun setupListeners() {
        btnStart.setOnClickListener {
            Prefs.setMonitoringEnabled(this, true)
            PaymentMonitorService.start(this)
            LogManager.add(this, "INFO", "Monitoring started")
            refreshUiState()
        }

        btnStop.setOnClickListener {
            Prefs.setMonitoringEnabled(this, false)
            PaymentMonitorService.stop(this)
            LogManager.add(this, "INFO", "Monitoring stopped")
            refreshUiState()
        }

        btnOpenAccess.setOnClickListener { openNotificationAccessSettings() }
        btnTestConnection.setOnClickListener { runFirebaseTest() }
        btnSettings.setOnClickListener { showSettingsDialog() }
        btnLogs.setOnClickListener { showLogsDialog() }
        btnHistory.setOnClickListener { showLogsDialog() }
    }

    private fun refreshUiState() {
        val hasAccess = isNotificationAccessGranted()
        tvAccessStatus.text = if (hasAccess) "CONNECTED ✓" else "NOT CONNECTED"
        tvAccessStatus.setTextColor(getColor(if (hasAccess) R.color.status_ok else R.color.status_error))

        val monitoring = Prefs.isMonitoringEnabled(this)
        val serviceRunning = hasAccess && monitoring
        tvServiceStatus.text = if (serviceRunning) "RUNNING ✓" else "STOPPED"
        tvServiceStatus.setTextColor(getColor(if (serviceRunning) R.color.status_ok else R.color.status_error))

        if (Prefs.isFirebaseConfigured(this)) {
            if (Prefs.isFirebaseConnected(this)) {
                tvFirebaseStatus.text = "CONNECTED ✓"
                tvFirebaseStatus.setTextColor(getColor(R.color.status_ok))
            } else {
                val lastCheck = Prefs.getFirebaseLastCheck(this)
                if (lastCheck > 0) {
                    tvFirebaseStatus.text = "FAILED"
                    tvFirebaseStatus.setTextColor(getColor(R.color.status_error))
                } else {
                    tvFirebaseStatus.text = "NOT TESTED"
                    tvFirebaseStatus.setTextColor(getColor(R.color.status_warn))
                }
            }
        } else {
            tvFirebaseStatus.text = "NOT CONFIGURED"
            tvFirebaseStatus.setTextColor(getColor(R.color.status_warn))
        }

        btnStart.isEnabled = !monitoring
        btnStop.isEnabled = monitoring

        refreshLastPayment()
    }

    private fun refreshLastPayment() {
        val summary = Prefs.getLastPaymentSummary(this)
        if (summary.isBlank()) {
            tvLastPayment.text = "No payments yet"
            return
        }
        try {
            val parts = summary.split("|")
            if (parts.size >= 6) {
                val status = parts[0]
                val method = parts[1].uppercase()
                val amount = parts[2]
                val phone = parts[3]
                val trx = parts[4]
                val time = parts[5]
                tvLastPayment.text = buildString {
                    append("Method: $method\n")
                    append("Amount: ৳$amount\n")
                    append("Sender: $phone\n")
                    append("TrxID: $trx\n")
                    append("Time: $time\n")
                    append("Status: $status")
                }
            } else {
                tvLastPayment.text = "No payments yet"
            }
        } catch (_: Exception) {
            tvLastPayment.text = "No payments yet"
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        return try {
            val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
            enabled.contains(packageName)
        } catch (_: Exception) {
            false
        }
    }

    private fun openNotificationAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) { }
            }
        }
    }

    private fun runFirebaseTest() {
        tvFirebaseStatus.text = "TESTING..."
        tvFirebaseStatus.setTextColor(getColor(R.color.status_warn))

        activityScope.launch {
            val (success, message) = withContext(Dispatchers.IO) {
                FirebaseHelper.testConnection(this@MainActivity)
            }
            withContext(Dispatchers.Main) {
                Prefs.setFirebaseLastCheck(this@MainActivity, System.currentTimeMillis())
                Prefs.setFirebaseConnected(this@MainActivity, success)

                if (success) {
                    tvFirebaseStatus.text = "CONNECTED ✓"
                    tvFirebaseStatus.setTextColor(getColor(R.color.status_ok))
                } else {
                    tvFirebaseStatus.text = "FAILED"
                    tvFirebaseStatus.setTextColor(getColor(R.color.status_error))
                }
                showToast(message)
            }
        }
    }

    private fun runFirebaseTestSilent() {
        activityScope.launch {
            val (success, _) = withContext(Dispatchers.IO) {
                FirebaseHelper.testConnection(this@MainActivity)
            }
            withContext(Dispatchers.Main) {
                Prefs.setFirebaseLastCheck(this@MainActivity, System.currentTimeMillis())
                Prefs.setFirebaseConnected(this@MainActivity, success)

                if (success) {
                    tvFirebaseStatus.text = "CONNECTED ✓"
                    tvFirebaseStatus.setTextColor(getColor(R.color.status_ok))
                } else {
                    tvFirebaseStatus.text = "FAILED"
                    tvFirebaseStatus.setTextColor(getColor(R.color.status_error))
                }
            }
        }
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        val etApiKey = view.findViewById<EditText>(R.id.etFirebaseApiKey)
        val etDbUrl = view.findViewById<EditText>(R.id.etFirebaseDbUrl)
        val etProjectId = view.findViewById<EditText>(R.id.etFirebaseProjectId)
        val etAppId = view.findViewById<EditText>(R.id.etFirebaseAppId)
        val etSenderId = view.findViewById<EditText>(R.id.etFirebaseSenderId)
        val etDataPath = view.findViewById<EditText>(R.id.etDataPath)

        etApiKey.setText(Prefs.getFirebaseApiKey(this))
        etDbUrl.setText(Prefs.getFirebaseDbUrl(this))
        etProjectId.setText(Prefs.getFirebaseProjectId(this))
        etAppId.setText(Prefs.getFirebaseAppId(this))
        etSenderId.setText(Prefs.getFirebaseSenderId(this))
        etDataPath.setText(Prefs.getDataPath(this))

        AlertDialog.Builder(this)
            .setTitle("Firebase Settings")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val apiKey = etApiKey.text.toString().trim()
                val dbUrl = etDbUrl.text.toString().trim()
                val projectId = etProjectId.text.toString().trim()
                val appId = etAppId.text.toString().trim()
                val senderId = etSenderId.text.toString().trim()
                val dataPath = etDataPath.text.toString().trim().ifBlank { "XNXANIKPAY" }

                if (apiKey.isBlank() || dbUrl.isBlank() || appId.isBlank()) {
                    showToast("API Key, Database URL, App ID required")
                    return@setPositiveButton
                }

                Prefs.setFirebaseApiKey(this, apiKey)
                Prefs.setFirebaseDbUrl(this, dbUrl)
                Prefs.setFirebaseProjectId(this, projectId)
                Prefs.setFirebaseAppId(this, appId)
                Prefs.setFirebaseSenderId(this, senderId)
                Prefs.setDataPath(this, dataPath)

                FirebaseHelper.reset()
                Prefs.setFirebaseConnected(this, false)
                Prefs.setFirebaseLastCheck(this, 0L)
                lastTestTime = 0L

                LogManager.add(this, "INFO", "Firebase config updated")
                refreshUiState()
                runFirebaseTestSilent()
                showToast("Settings saved ✓")
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ ->
                Prefs.clearFirebaseConfig(this)
                FirebaseHelper.reset()
                Prefs.setFirebaseConnected(this, false)
                Prefs.setFirebaseLastCheck(this, 0L)
                lastTestTime = 0L
                LogManager.add(this, "INFO", "Firebase config cleared")
                refreshUiState()
                showToast("Firebase config cleared")
            }
            .show()
    }

    private fun showLogsDialog() {
        val logs = LogManager.getList(this)
        val message = if (logs.isEmpty()) "No logs yet" else logs.joinToString("\n\n")

        val scrollView = ScrollView(this).apply {
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#0A0A1A"))
        }

        val textView = TextView(this).apply {
            text = message
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 12f
            setLineSpacing(8f, 1f)
            setPadding(10, 10, 10, 10)
            setTextIsSelectable(true)
        }

        scrollView.addView(
            textView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("📋 Logs")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear") { _, _ ->
                LogManager.clear(this)
                showToast("Logs cleared")
            }
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.background_dark)
            val titleId = resources.getIdentifier("alertTitle", "id", "android")
            if (titleId > 0) {
                dialog.findViewById<TextView>(titleId)?.setTextColor(Color.WHITE)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#3B6BFF"))
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor("#FF9800"))
        }

        dialog.show()
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIF_PERMISSION_CODE
                )
            }
        }
    }

    private fun requestSmsPermissionIfNeeded() {
        val hasReceive = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasRead = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReceive || !hasRead) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                ),
                SMS_PERMISSION_CODE
            )
        }
    }
}
