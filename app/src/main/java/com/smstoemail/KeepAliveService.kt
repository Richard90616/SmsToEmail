package com.smstoemail

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 前台保活服务。
 *
 * Android 8.0+ 必须通过 startForegroundService() 启动后台服务, 并在 5 秒内
 * 调用 startForeground() 展示通知, 否则系统会 ANR。
 *
 * 仅当用户开启 "keepAlive" 时才启动, 否则不影响续航。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFY_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFY_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "startForeground 失败: ${e.message}", e)
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SmsApp.CHANNEL_KEEPALIVE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("短信转发服务运行中")
            .setContentText("收到短信时自动转发到邮箱")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}