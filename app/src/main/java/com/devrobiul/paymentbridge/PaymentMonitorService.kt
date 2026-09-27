package com.devrobiul.paymentbridge

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat

class PaymentMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "devrobiul_payment_bridge_monitor"
        const val NOTIFICATION_ID = 3001

        const val ACTION_START = "com.devrobiul.paymentbridge.START"
        const val ACTION_STOP = "com.devrobiul.paymentbridge.STOP"
        const val ACTION_RESTART = "com.devrobiul.paymentbridge.RESTART"

        private const val RESTART_REQUEST_CODE = 3001
        private const val RESTART_DELAY_MS = 1500L

        fun start(context: Context) {
            val intent = Intent(context, PaymentMonitorService::class.java)
            intent.action = ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PaymentMonitorService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }

        fun scheduleRestart(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, PaymentMonitorService::class.java).apply {
                    action = ACTION_RESTART
                }
                val pendingIntent = PendingIntent.getService(
                    context,
                    RESTART_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val triggerAt = SystemClock.elapsedRealtime() + RESTART_DELAY_MS

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerAt,
                            pendingIntent
                        )
                    } catch (_: SecurityException) {
                        alarmManager.set(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerAt,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            } catch (_: Exception) { }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.setMonitoringEnabled(this, false)
                cancelRestartAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                if (Prefs.isMonitoringEnabled(this)) {
                    try {
                        startForeground(NOTIFICATION_ID, buildNotification())
                    } catch (_: Exception) { }
                }
                return START_STICKY
            }
            else -> {
                Prefs.setMonitoringEnabled(this, true)
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
                } catch (_: Exception) { }
                return START_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (Prefs.isMonitoringEnabled(this)) {
            scheduleRestart(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Prefs.isMonitoringEnabled(this)) {
            scheduleRestart(this)
        }
    }

    private fun cancelRestartAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, PaymentMonitorService::class.java).apply {
                action = ACTION_RESTART
            }
            val pendingIntent = PendingIntent.getService(
                this,
                RESTART_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) { }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, PaymentMonitorService::class.java)
        stopIntent.action = ACTION_STOP
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val startIntent = Intent(this, PaymentMonitorService::class.java)
        startIntent.action = ACTION_START
        val startPending = PendingIntent.getService(
            this, 2, startIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isMonitoring = Prefs.isMonitoringEnabled(this)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dev Robiul Payment Bridge")
            .setContentText(
                if (isMonitoring) "Monitoring payments — tap to open"
                else "Monitoring is paused"
            )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (isMonitoring) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPending)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "START", startPending)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Payment Bridge Monitor"
            val descriptionText = "Keeps Dev Robiul Payment Bridge running in background"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
