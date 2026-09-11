package com.smstoemail

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * 邮件发送服务。
 *
 * 通过 JavaMail for Android 走 SMTP 发送纯文本邮件。
 * 全部在 IO 协程执行，不阻塞广播。
 */
object EmailService {

    private const val TAG = "EmailService"

    /** 同步发送, 必须在 IO 线程调用 */
    fun send(
        host: String,
        port: Int,
        useSsl: Boolean,
        from: String,
        password: String,
        to: String,
        subject: String,
        body: String
    ): Result<Unit> {
        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            // SSL (465) / STARTTLS (587) 自适应
            if (useSsl) {
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.port", port.toString())
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            // 超时: 短信转发场景, 网络卡顿宁可失败也别把广播阻塞太久
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.connectiontimeout", "10000")
            // 启用调试日志 (按需开启)
            put("mail.debug", "false")
        }

        return try {
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(from, password)
            })
            val msg = MimeMessage(session).apply {
                try { setFrom(InternetAddress(from)) } catch (e: Exception) {
                    Log.e(TAG, "setFrom failed: ${e.message}")
                    return Result.failure(e)
                }
                try {
                    setRecipients(
                        Message.RecipientType.TO,
                        InternetAddress.parse(to.replace("，", ",").replace(";", ","))
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "setRecipients failed: ${e.message}")
                    return Result.failure(e)
                }
                try { setSubject(subject, "UTF-8") } catch (_: Exception) { /* ok */ }
                try { setText(body, "UTF-8") } catch (_: Exception) { /* ok */ }
            }
            try {
                Transport.send(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Transport.send failed: ${e.message}")
                return Result.failure(e)
            }
            Log.i(TAG, "邮件发送成功 -> $to (subject=$subject)")
            Result.success(Unit)
        } catch (e: Throwable) {
            // Throwable 兜底: OutOfMemoryError / StackOverflowError 也吞
            Log.e(TAG, "send threw: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(if (e is Exception) e else RuntimeException(e))
        }
    }

    /** 协程封装 */
    suspend fun sendAsync(
        host: String,
        port: Int,
        useSsl: Boolean,
        from: String,
        password: String,
        to: String,
        subject: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            send(host, port, useSsl, from, password, to, subject, body)
        } catch (t: Throwable) {
            Log.e(TAG, "sendAsync top-level: ${t.message}", t)
            Result.failure(if (t is Exception) t else RuntimeException(t))
        }
    }
}