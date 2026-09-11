package com.smstoemail

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 状态通知工具：成功 / 失败 / 测试消息。
 * 不打扰: 用 NotificationCompat 并标 Importance_LOW 走静默通道。
 */
object Notifier {

    fun notifySuccess(context: Context, sender: String, bodyPreview: String) {
        val n = build(
            context,
            "短信已转发",
            "$sender: $bodyPreview",
        )
        notify(context, 1001, n)
    }

    fun notifyFailure(context: Context, sender: String, error: String) {
        val n = build(
            context,
            "短信转发失败",
            "$sender -> $error",
        )
        n.color = 0xFFD32F2F.toInt()
        notify(context, 1002, n)
    }

    fun notifyTest(context: Context, msg: String, success: Boolean) {
        val n = build(
            context,
            if (success) "测试邮件已发送" else "测试邮件失败",
            msg
        )
        if (!success) n.color = 0xFFD32F2F.toInt()
        notify(context, 1003, n)
    }

    private fun build(context: Context, title: String, text: String): NotificationCompat.Builder {
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, SmsApp.CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_sys_email)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun notify(context: Context, id: Int, n: NotificationCompat.Builder) {
        // Android 13+ 需要运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, n.build())
    }
}