package com.smstoemail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    private val pendingResult by lazy { goAsync() }
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val cfg = ConfigManager(context)
        if (!cfg.enabled) {
            Log.d(TAG, "disabled, skip")
            pendingResult.finish()
            return
        }
        if (!cfg.isConfigured()) {
            SmsLogStore.add(SmsLogStore.Level.FAIL, "?", "mail not configured, drop")
            pendingResult.finish()
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: emptyArray()
        if (messages.isEmpty()) {
            pendingResult.finish()
            return
        }

        val sender = messages[0].displayOriginatingAddress ?: "unknown"
        val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }
        val ts = messages[0].timestampMillis

        if (!passWhitelist(sender, cfg.whitelist)) {
            SmsLogStore.add(SmsLogStore.Level.INFO, sender, "whitelist filtered, skip")
            pendingResult.finish()
            return
        }
        if (!passKeyword(body, cfg.keywordFilter)) {
            SmsLogStore.add(SmsLogStore.Level.INFO, sender, "keyword filtered, skip")
            pendingResult.finish()
            return
        }
        if (DedupStore.isDuplicate(sender, body, ts, cfg.dedupWindowSec)) {
            SmsLogStore.add(SmsLogStore.Level.INFO, sender, "dedup hit, skip")
            pendingResult.finish()
            return
        }

        val timeStr = timeFmt.format(Date(ts))
        val subject = "[SMS] $sender @ $timeStr"
        val sep = "-".repeat(36)
        val fullBody = buildString {
            appendLine("Sender:    $sender")
            appendLine("Time:      $timeStr")
            appendLine("Device:    ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android:   ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine(sep)
            append(body)
        }

        SmsLogStore.add(SmsLogStore.Level.INFO, sender, "received (len=${body.length}), forwarding...")

        // PARTIAL_WAKE_LOCK: 防止 CPU 休眠杀死进行中的 SSL 握手/SMTP 发送
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsToEmail::SendMail")
        wl.setReferenceCounted(false)
        try { wl.acquire(15_000L) } catch (_: Throwable) { }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = EmailService.sendAsync(
                    host = cfg.smtpHost,
                    port = cfg.smtpPort,
                    useSsl = cfg.smtpSsl,
                    from = cfg.mailFrom,
                    password = cfg.mailPassword,
                    to = cfg.mailTo,
                    subject = subject,
                    body = fullBody
                )
                result.onSuccess {
                    SmsLogStore.add(SmsLogStore.Level.SUCCESS, sender, "delivered to ${cfg.mailTo}")
                    Notifier.notifySuccess(context, sender, body.take(50))
                }.onFailure { e ->
                    val msg = e.message ?: "unknown"
                    SmsLogStore.add(SmsLogStore.Level.FAIL, sender, "send failed: $msg (enqueue retry)")
                    Notifier.notifyFailure(context, sender, msg)
                    RetryWorker.enqueue(context, sender, subject, fullBody)
                }
            } catch (t: Throwable) {
                SmsLogStore.add(SmsLogStore.Level.FAIL, sender, "exception: ${t.message}")
            } finally {
                try { if (wl.isHeld) wl.release() } catch (_: Throwable) { }
                pendingResult.finish()
            }
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
