package com.example.ntfypop.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.ntfypop.core.Constants

/**
 * 开机 / 应用更新后自动恢复接收。
 * 注意：Android 15 起 BOOT_COMPLETED 禁止启动 dataSync 等类型的前台服务，
 * 而我们用的是 specialUse，不在禁止名单内。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        try {
            LinkService.start(context)
            Log.i(Constants.LOG_TAG, "开机自启：已启动接收服务")
        } catch (e: Exception) {
            // 例如 ForegroundServiceStartNotAllowedException：降级为一条提示通知，绝不崩溃
            Log.w(Constants.LOG_TAG, "开机自启失败: " + e.message)
            NotificationFactory.postFallback(context, "Heibuling", "点我恢复接收")
        }
    }
}
