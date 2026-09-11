package com.smstoemail

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * 应用入口。
 * - 创建前台保活通知 Channel
 * - 全局异常兜底，防止进程崩掉后不再监听
 */
class SmsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_KEEPALIVE,
                    "短信转发保活",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "保持监听短信广播"
                    setShowBadge(false)
                }
            )
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS,
                    "转发状态",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "短信转发结果通知"
                }
            )
        }

        // 兜底：捕获未处理异常，写入日志
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("SmsApp", "uncaught on $thread", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CHANNEL_KEEPALIVE = "channel_keepalive"
        const val CHANNEL_STATUS = "channel_status"
    }
}