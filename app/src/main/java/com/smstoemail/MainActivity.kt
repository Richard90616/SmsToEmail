package com.smstoemail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smstoemail.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * 主界面：配置邮件账号、白名单、关键字，并提供「测试发送」按钮。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cfg by lazy { ConfigManager(this) }

    private val requestSmsPerm = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val smsGranted = granted[Manifest.permission.RECEIVE_SMS] == true ||
            granted[Manifest.permission.READ_SMS] == true
        binding.tvPermSms.text = if (smsGranted) "✅ 已授权" else "❌ 未授权 (将无法接收)"
    }

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        binding.tvPermNotif.text = if (granted) "✅ 已授权" else "❌ 未授权 (将无通知)"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadConfigToUi()
        updatePermissionStatus()
        binding.tvHelp.movementMethod = LinkMovementMethod.getInstance()

        binding.btnSave.setOnClickListener {
            if (saveConfigFromUi()) {
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
                binding.tvSummary.text = cfg.summary()
            }
        }

        binding.btnRequestSms.setOnClickListener {
            requestSmsPerm.launch(arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            ))
        }

        binding.btnRequestNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "当前系统无需此权限", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnTest.setOnClickListener {
            doTestSend()
        }

        binding.switchEnable.setOnCheckedChangeListener { _, checked ->
            cfg.enabled = checked
            if (checked && cfg.keepAlive) KeepAliveService.start(this)
            if (!checked) KeepAliveService.stop(this)
        }

        binding.switchKeepAlive.setOnCheckedChangeListener { _, checked ->
            cfg.keepAlive = checked
            if (checked) KeepAliveService.start(this) else KeepAliveService.stop(this)
        }
    }

    private fun loadConfigToUi() {
        binding.etSmtpHost.setText(cfg.smtpHost)
        binding.etSmtpPort.setText(cfg.smtpPort.toString())
        binding.cbSsl.isChecked = cfg.smtpSsl
        binding.etMailFrom.setText(cfg.mailFrom)
        binding.etMailPassword.setText(cfg.mailPassword)
        binding.etMailTo.setText(cfg.mailTo)
        binding.etWhitelist.setText(cfg.whitelist)
        binding.etKeyword.setText(cfg.keywordFilter)
        binding.etDedup.setText(cfg.dedupWindowSec.toString())
        binding.switchEnable.isChecked = cfg.enabled
        binding.switchKeepAlive.isChecked = cfg.keepAlive
        binding.tvSummary.text = cfg.summary()
    }

    private fun saveConfigFromUi(): Boolean {
        val from = binding.etMailFrom.text.toString().trim()
        val to = binding.etMailTo.text.toString().trim()
        val pass = binding.etMailPassword.text.toString().trim()
        if (from.isBlank() || to.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "发件人/收件人/密码不能为空", Toast.LENGTH_SHORT).show()
            return false
        }
        cfg.smtpHost = binding.etSmtpHost.text.toString().trim().ifBlank { "smtp.qq.com" }
        cfg.smtpPort = binding.etSmtpPort.text.toString().toIntOrNull() ?: 465
        cfg.smtpSsl = binding.cbSsl.isChecked
        cfg.mailFrom = from
        cfg.mailPassword = pass
        cfg.mailTo = to
        cfg.whitelist = binding.etWhitelist.text.toString().trim()
        cfg.keywordFilter = binding.etKeyword.text.toString().trim()
        cfg.dedupWindowSec = binding.etDedup.text.toString().toIntOrNull() ?: 60
        return true
    }

    private fun updatePermissionStatus() {
        val smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        binding.tvPermSms.text = if (smsGranted) "✅ 已授权" else "❌ 未授权"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            binding.tvPermNotif.text = if (notifGranted) "✅ 已授权" else "❌ 未授权"
        } else {
            binding.tvPermNotif.text = "✅ 系统无需"
        }
    }

    private fun doTestSend() {
        if (!saveConfigFromUi()) return
        lifecycleScope.launch {
            val r = EmailService.sendAsync(
                host = cfg.smtpHost,
                port = cfg.smtpPort,
                useSsl = cfg.smtpSsl,
                from = cfg.mailFrom,
                password = cfg.mailPassword,
                to = cfg.mailTo,
                subject = "[SmsToEmail] 测试邮件",
                body = buildString {
                    appendLine("这是一封测试邮件。")
                    appendLine("如果你收到此邮件, 表明 SMTP 配置正确。")
                    appendLine("时间: ${java.util.Date()}")
                    appendLine("机型: ${Build.MANUFACTURER} ${Build.MODEL}")
                }
            )
            val msg = if (r.isSuccess) "已发送, 请检查邮箱" else "失败: ${r.exceptionOrNull()?.message}"
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            Notifier.notifyTest(this@MainActivity, msg, r.isSuccess)
        }
    }
}