package com.smstoemail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机启动后, 如果用户开启了"保活", 自动拉起 KeepAliveService。
 *
 * 注意: 国产 ROM (小米/OPPO/vivo/华为) 默认禁止自启动, 用户需手动在
 * "设置 → 应用 → 自启动"中开启, 否则开机后服务不会起来。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val cfg = ConfigManager(context)
        if (!cfg.keepAlive) {
            Log.d(TAG, "未开启保活, 不启动服务")
            return
        }
        KeepAliveService.start(context)
    }

    companion object { private const val TAG = "BootReceiver" }
}