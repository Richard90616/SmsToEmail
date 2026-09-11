package com.smstoemail

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            Log.d(TAG, "tick: ${i.action}")
            try {
                startForeground(NOTIFY_ID, buildNotification())
                SmsLogStore.add(SmsLogStore.Level.INFO, "system", "lifecycle: ${i.action}")
            } catch (t: Throwable) {
                Log.w(TAG, "self-check failed: ${t.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(tickReceiver, filter, RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(tickReceiver, filter)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "registerReceiver failed: ${t.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFY_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFY_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(tickReceiver) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                SmsApp.CHANNEL_KEEPALIVE,
                "SMS KeepAlive",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "keeps SMS forwarding service alive" }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SmsApp.CHANNEL_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_notification_email)
            .setContentTitle("SMS forwarding running")
            .setContentText("incoming SMS will be forwarded to your email")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val NOTIFY_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "startForegroundService failed: ${t.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
