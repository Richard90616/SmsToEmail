package com.smstoemail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 短信接收器 (加固版, 修闪退)。
 *
 * 关键改动：
 *  1. 使用专用 HandlerThread 而不是 CoroutineScope — 更可控, 进程被回收时不挂
 *  2. onReceive 整层 try/catch, 任何崩溃都被捕获并写入 logcat, 不杀死进程
 *  3. goAsync() 直接调用, 不再用 lazy (避免冷启动时序陷阱)
 *  4. WakeLock 加 / 释放 / Notifier 全部 try/catch, 不让任何事件击穿进 finally
 *  5. 所有重试、Notifier 都用 HandlerThread post, 主线程只负责接收广播快速返回
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // goAsync 必须在 onReceive 期间直接调用
        val pendingResult = try {
            goAsync()
        } catch (t: Throwable) {
            Log.e(TAG, "goAsync failed: ${t.message}", t)
            return
        }

        // 专用 worker 线程, 处理耗时工作 (SMTP I/O, 重试)
        val worker = HandlerThread("SmsToEmail-worker")
        try {
            worker.start()
        } catch (t: Throwable) {
            Log.e(TAG, "HandlerThread start failed", t)
            try { pendingResult.finish() } catch (_: Throwable) { }
            return
        }
        val handler = Handler(worker.looper)

        try {
            handler.post {
                try {
                    handleSms(context.applicationContext, intent)
                } catch (t: Throwable) {
                    Log.e(TAG, "handleSms crash", t)
                } finally {
                    try { pendingResult.finish() } catch (_: Throwable) { }
                    try { worker.quitSafely() } catch (_: Throwable) { }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "post failed", t)
            try { pendingResult.finish() } catch (_: Throwable) { }
            try { worker.quitSafely() } catch (_: Throwable) { }
        }
    }

    private fun handleSms(context: Context, intent: Intent) {
        val cfg = try { ConfigManager(context) } catch (t: Throwable) {
            Log.e(TAG, "ConfigManager init failed", t); return
        }

        try {
            if (!cfg.enabled) return
        } catch (t: Throwable) { Log.w(TAG, "cfg.enabled read failed: ${t.message}"); return }

        if (!cfg.isConfigured()) {
            SmsLogStore.add(SmsLogStore.Level.FAIL, "?", "mail not configured")
            return
        }

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: emptyArray()
        } catch (t: Throwable) {
            Log.e(TAG, "parse SMS failed", t); return
        }
        if (messages.isEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: "unknown"
        val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }
        val ts = messages[0].timestampMillis

        if (!passWhitelist(sender, cfg.whitelist)) {
            SmsLogStore.add(SmsLogStore.Level.INFO, sender, "whitelist filtered")
            return
        }
        if (!passKeyword(body, cfg.keywordFilter)) {
            SmsLogStore.add(SmsLogStore.Level.INFO, sender, "keyword filtered")
            return
        }
        if (DedupStore.isDuplicate(sender, body, ts, cfg.dedupWindowSec)) {
            SmsLogStore.add(SmsLogStore.Level.INFO, sender, "dedup hit")
            return
        }

        val timeStr = try { timeFmt.format(Date(ts)) } catch (_: Throwable) { "?" }
        val subject = "[SMS] $sender @ $timeStr"
        val fullBody = buildString {
            appendLine("Sender:    $sender")
            appendLine("Time:      $timeStr")
            appendLine("Device:    ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android:   ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("-".repeat(36))
            append(body)
        }

        SmsLogStore.add(SmsLogStore.Level.INFO, sender, "received (len=${body.length}), forwarding")

        // PARTIAL_WAKE_LOCK: 防止 CPU 休眠杀掉 SSL 握手
        val wl = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsToEmail::SendMail")
                ?.also { it.setReferenceCounted(false) }
        } catch (t: Throwable) {
            Log.w(TAG, "wakelock create failed: ${t.message}"); null
        }
        try { wl?.acquire(15_000L) } catch (t: Throwable) { Log.w(TAG, "wakelock acquire failed: ${t.message}") }

        try {
            // HandlerThread 不是协程作用域, 改用同步版 send()
            // 本来就在 worker thread 上跑, 不会阻塞 UI
            val result = EmailService.send(
                host = cfg.smtpHost,
                port = cfg.smtpPort,
                useSsl = cfg.smtpSsl,
                from = cfg.mailFrom,
                password = cfg.mailPassword,
                to = cfg.mailTo,
                subject = subject,
                body = fullBody
            )
            result.fold(
                onSuccess = {
                    SmsLogStore.add(SmsLogStore.Level.SUCCESS, sender, "delivered to ${cfg.mailTo}")
                    safeNotify { Notifier.notifySuccess(context, sender, body.take(50)) }
                },
                onFailure = { e ->
                    val msg = e.message ?: "unknown"
                    SmsLogStore.add(SmsLogStore.Level.FAIL, sender, "send failed: $msg -> retry queued")
                    safeNotify { Notifier.notifyFailure(context, sender, msg) }
                    try { RetryWorker.enqueue(context, sender, subject, fullBody) }
                    catch (t: Throwable) { Log.e(TAG, "enqueue retry failed", t) }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "send threw exception", t)
            SmsLogStore.add(SmsLogStore.Level.FAIL, sender, "exception: ${t.message}")
        } finally {
            try { if (wl?.isHeld == true) wl.release() } catch (_: Throwable) { }
        }
    }

    /** 把通知发送到主线程, 任何异常都被吞 (通知不应该拖死后台 worker) */
    private fun safeNotify(block: () -> Unit) {
        try {
            val mainHandler = android.os.Looper.getMainLooper().let { Handler(it) }
            mainHandler.post {
                try { block() } catch (t: Throwable) {
                    Log.w(TAG, "notify crashed (swallowed): ${t.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "safeNotify dispatch failed: ${t.message}")
        }
    }

    private fun passWhitelist(sender: String, whitelist: String): Boolean {
        if (whitelist.isBlank()) return true
        return whitelist.split(",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { sender.contains(it) }
    }

    private fun passKeyword(body: String, pattern: String): Boolean {
        if (pattern.isBlank()) return true
        return try {
            Regex(pattern).containsMatchIn(body)
        } catch (e: Exception) {
            Log.w(TAG, "bad regex ($pattern): ${e.message}")
            true
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
        private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    }
}

object DedupStore {
    private data class Fingerprint(val sender: String, val body: String, val tsBucket: Long)
    private val seen = ArrayDeque<Fingerprint>()

    @Synchronized
    fun isDuplicate(sender: String, body: String, ts: Long, windowSec: Int): Boolean {
        val windowMs = windowSec.coerceAtLeast(1) * 1000L
        val bucket = ts / windowMs
        val fp = Fingerprint(sender, body.take(64), bucket)
        if (seen.any { it == fp }) return true
        seen.addLast(fp)
        while (seen.size > 200) seen.removeFirst()
        return false
    }
}
