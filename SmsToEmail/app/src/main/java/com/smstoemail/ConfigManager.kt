package com.smstoemail

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 配置管理。
 *
 * 邮件凭据（密码 / 授权码）使用 EncryptedSharedPreferences 加密存储，
 * 依赖 androidx.security:security-crypto。密钥由 Android Keystore 派生。
 *
 * 配置项：
 * - smtp_host / smtp_port / smtp_ssl
 * - mail_from / mail_password
 * - mail_to
 * - whitelist（号码白名单，逗号分隔；为空表示不过滤）
 * - keyword_filter（关键字正则；含此关键字才转发；为空表示不限制）
 * - enabled（总开关）
 * - dedup_window_sec（去重窗口，秒）
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Keystore 不可用时降级为普通 SharedPreferences（仅用于无密钥环境）
        android.util.Log.w("ConfigManager", "EncryptedSharedPreferences 不可用, 降级: ${e.message}")
        context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    var smtpHost: String
        get() = prefs.getString(KEY_SMTP_HOST, "smtp.qq.com") ?: "smtp.qq.com"
        set(v) = prefs.edit().putString(KEY_SMTP_HOST, v).apply()

    var smtpPort: Int
        get() = prefs.getInt(KEY_SMTP_PORT, 465)
        set(v) = prefs.edit().putInt(KEY_SMTP_PORT, v).apply()

    /** true=SMTPS(465/SSL)，false=STARTTLS(587) */
    var smtpSsl: Boolean
        get() = prefs.getBoolean(KEY_SMTP_SSL, true)
        set(v) = prefs.edit().putBoolean(KEY_SMTP_SSL, v).apply()

    var mailFrom: String
        get() = prefs.getString(KEY_MAIL_FROM, "") ?: ""
        set(v) = prefs.edit().putString(KEY_MAIL_FROM, v).apply()

    var mailPassword: String
        get() = prefs.getString(KEY_MAIL_PASSWORD, "") ?: ""
        set(v) = prefs.edit().putString(KEY_MAIL_PASSWORD, v).apply()

    var mailTo: String
        get() = prefs.getString(KEY_MAIL_TO, "") ?: ""
        set(v) = prefs.edit().putString(KEY_MAIL_TO, v).apply()

    /** 逗号分隔的手机号；为空表示全部转发 */
    var whitelist: String
        get() = prefs.getString(KEY_WHITELIST, "") ?: ""
        set(v) = prefs.edit().putString(KEY_WHITELIST, v).apply()

    /** 关键字正则；匹配到才转发；为空表示不过滤 */
    var keywordFilter: String
        get() = prefs.getString(KEY_KEYWORD, "") ?: ""
        set(v) = prefs.edit().putString(KEY_KEYWORD, v).apply()

    var dedupWindowSec: Int
        get() = prefs.getInt(KEY_DEDUP, 60)
        set(v) = prefs.edit().putInt(KEY_DEDUP, v).apply()

    var keepAlive: Boolean
        get() = prefs.getBoolean(KEY_KEEPALIVE, false)
        set(v) = prefs.edit().putBoolean(KEY_KEEPALIVE, v).apply()

    fun isConfigured(): Boolean =
        mailFrom.isNotBlank() && mailPassword.isNotBlank() && mailTo.isNotBlank()

    fun summary(): String = buildString {
        append("SMTP=$smtpHost:$smtpPort(${if (smtpSsl) "SSL" else "STARTTLS"})\n")
        append("FROM=$mailFrom\nTO=$mailTo\n")
        append("白名单=${if (whitelist.isBlank()) "全部" else whitelist}\n")
        append("关键字=${if (keywordFilter.isBlank()) "无" else keywordFilter}\n")
        append("去重窗口=${dedupWindowSec}s\n")
        append("保活=$keepAlive\n")
    }

    companion object {
        private const val PREFS_NAME = "sms_to_email_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SMTP_SSL = "smtp_ssl"
        private const val KEY_MAIL_FROM = "mail_from"
        private const val KEY_MAIL_PASSWORD = "mail_password"
        private const val KEY_MAIL_TO = "mail_to"
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_KEYWORD = "keyword"
        private const val KEY_DEDUP = "dedup"
        private const val KEY_KEEPALIVE = "keepalive"
    }
}