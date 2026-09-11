package com.smstoemail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 短信接收器。
 *
 * 监听 SMS_RECEIVED 广播, 解析 PDU 后:
 * 1) 白名单过滤
 * 2) 关键字过滤
 * 3) 去重（短时间内相同发件人+内容只转发一次）
 * 4) 异步发送邮件
 *
 * BroadcastReceiver 在 Android 中执行时间极短（约 10 秒），因此邮件发送
 * 必须用 goAsync() + 协程，否则会被系统 kill 掉。
 */
class SmsReceiver : BroadcastReceiver() {

    private val pendingResult by lazy { goAsync() }
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val cfg = ConfigManager(context)
        if (!cfg.enabled) {
            Log.d(TAG, "总开关未启用, 跳过")
            pendingResult.finish()
            return
        }
        if (!cfg.isConfigured()) {
            Log.w(TAG, "邮件未配置, 跳过")
            pendingResult.finish()
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: emptyArray()
        if (messages.isEmpty()) {
            pendingResult.finish()
            return
        }

        // 拼拼接号(分片短信)
        val sender = messages[0].displayOriginatingAddress ?: "unknown"
        val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }
        val ts = messages[0].timestampMillis

        // 过滤
        if (!passWhitelist(sender, cfg.whitelist)) {
            Log.d(TAG, "号码 $sender 不在白名单, 跳过")
            pendingResult.finish()
            return
        }
        if (!passKeyword(body, cfg.keywordFilter)) {
            Log.d(TAG, "内容不匹配关键字 ${cfg.keywordFilter}, 跳过")
            pendingResult.finish()
            return
        }
        if (DedupStore.isDuplicate(sender, body, ts, cfg.dedupWindowSec)) {
            Log.d(TAG, "去重命中, 跳过 ($sender)")
            pendingResult.finish()
            return
        }

        val timeStr = timeFmt.format(Date(ts))
        val subject = "[SMS] $sender @ $timeStr"
        val fullBody = buildString {
            appendLine("发件人: $sender")
            appendLine("时间: $timeStr")
            appendLine("机型: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("─" * 40)
            append(body)
        }

        CoroutineScope(Dispatchers.IO).launch {
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
                Notifier.notifySuccess(context, sender, body.take(50))
            }.onFailure {
                Notifier.notifyFailure(context, sender, it.message ?: "未知错误")
            }
            pendingResult.finish()
        }
    }

    /** 白名单: 逗号分隔, 空表示全部 */
    private fun passWhitelist(sender: String, whitelist: String): Boolean {
        if (whitelist.isBlank()) return true
        return whitelist.split(",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { sender.contains(it) }
    }

    /** 关键字: 正则, 空表示全部 */
    private fun passKeyword(body: String, pattern: String): Boolean {
        if (pattern.isBlank()) return true
        return try {
            Regex(pattern).containsMatchIn(body)
        } catch (e: Exception) {
            Log.w(TAG, "关键字正则非法 ($pattern): ${e.message}")
            true
        }
    }

    private operator fun String.times(n: Int): String = repeat(n)

    companion object {
        private const val TAG = "SmsReceiver"
    }
}

/**
 * 进程内去重缓存。
 * 简单实现：内存 LRU + 时间窗口，进程被杀后丢失（重发概率极低）。
 * 如果要更严谨可改写 Room/SQLite。
 */
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
        // 控制内存
        while (seen.size > 200) seen.removeFirst()
        return false
    }
}