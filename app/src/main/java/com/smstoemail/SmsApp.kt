package com.smstoemail

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class SmsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_KEEPALIVE,
                    "SMS forwarding keep-alive",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "keeps SMS receiver alive"
                    setShowBadge(false)
                }
            )
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS,
                    "Forwarding status",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "SMS forwarding result"
                }
            )
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("SmsApp", "uncaught on $thread", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CHANNEL_KEEPALIVE = "channel_keepalive"
        const val CHANNEL_STATUS = "channel_status"

        @Volatile
        var appContext: Context? = null
            private set
    }
}
