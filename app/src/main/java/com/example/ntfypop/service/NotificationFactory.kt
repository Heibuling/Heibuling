package com.example.ntfypop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.ntfypop.MainActivity
import com.example.ntfypop.core.Constants
import java.util.Calendar

/**
 * 常驻通知文案 = 主问候（分时段池，同小时稳定 + 最近 8 条 LRU 去重）+ 数据彩蛋（约 30%）+ 状态副词。
 * 硬约束：不含 topic / uid / 未读数；深夜只用安静组；状态绝不撒谎。
 */
object NotificationFactory {

    private const val CHANNEL_ACK = "ntfypop_ack"
    private const val CHANNEL_FALLBACK = "ntfypop_fallback"
    private const val NOTIF_ID_ACK = 1002
    private const val NOTIF_ID_FALLBACK = 1003

    // 缓存键必须同时包含「小时 + 昵称」：只按小时缓存会导致服务启动时用占位名算出的问候语
    // 在整个小时内一直生效（实测表现为通知里永远显示「早上好，我」而不是用户昵称）
    private var lastKey = ""
    private var cachedTitle = ""
    private val recent = ArrayDeque<String>()

    private var startedAt = System.currentTimeMillis()
    private var bubblesToday = 0
    private var day = -1
    private var lastMessageAt = 0L

    fun onBubbleShown() {
        rollDay()
        bubblesToday++
    }

    fun onMessageReceived() {
        lastMessageAt = System.currentTimeMillis()
    }

    private fun rollDay() {
        val d = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (d != day) {
            day = d
            bubblesToday = 0
        }
    }

    /** 服务刚起来、还没读到昵称时的中性文案。 */
    fun starting(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, Constants.NOTIF_CHANNEL_PERSISTENT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Heibuling 正在启动…")
            .setContentText("马上就好")
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(Constants.NOTIF_CHANNEL_PERSISTENT) == null) {
            val ch = NotificationChannel(
                Constants.NOTIF_CHANNEL_PERSISTENT, "保持连接", NotificationManager.IMPORTANCE_LOW,
            )
            ch.setShowBadge(false)
            ch.enableVibration(false)
            ch.setSound(null, null)
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(CHANNEL_ACK) == null) {
            val ch = NotificationChannel(CHANNEL_ACK, "回执提醒", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            ch.enableVibration(false)
            ch.setSound(null, null)
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(CHANNEL_FALLBACK) == null) {
            val ch = NotificationChannel(CHANNEL_FALLBACK, "消息提醒", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
        }
    }

    fun persistent(context: Context, name: String, connected: Boolean): Notification {
        ensureChannels(context)
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, Constants.NOTIF_CHANNEL_PERSISTENT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(greeting(name))
            .setContentText(statusText(connected))
            .setContentIntent(pi)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 对方点掉气泡时的静默通知。 */
    fun ackNotification(context: Context, text: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_ACK)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(text)
            .setAutoCancel(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 没有悬浮窗权限时的降级通知（否则对方完全收不到）。 */
    fun fallbackNotification(context: Context, sender: String, text: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_FALLBACK)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(sender)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
    }

    fun post(context: Context, id: Int, n: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(id, n)
        } catch (e: Exception) {
        }
    }

    fun postAck(context: Context, text: String) = post(context, NOTIF_ID_ACK, ackNotification(context, text))

    fun postFallback(context: Context, sender: String, text: String) =
        post(context, NOTIF_ID_FALLBACK, fallbackNotification(context, sender, text))

    fun statusText(connected: Boolean): String = if (connected) {
        listOf("通道畅通", "我盯着呢", "待命中", "同步正常").random()
    } else {
        listOf("正在找回信号…", "连接断了，点我救一下", "重连中，别急").random()
    }

    fun onServiceStarted() {
        startedAt = System.currentTimeMillis()
    }

    /** 主问候：同一小时内稳定，最近 8 条不重复，深夜只用安静组，约 30% 概率叠加真实数据彩蛋。 */
    fun greeting(name: String): String {
        val now = System.currentTimeMillis()
        val key = (now / 3_600_000L).toString() + "|" + name
        if (key == lastKey && cachedTitle.isNotEmpty()) return cachedTitle

        val pool = poolFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), name)
        val fresh = pool.filterNot { recent.contains(it) }
        val pick = (if (fresh.isNotEmpty()) fresh else pool).random()
        recent.addLast(pick)
        while (recent.size > 8) recent.removeFirst()

        val withEgg = maybeEgg(name)?.let { pick + "　" + it } ?: pick
        cachedTitle = if (withEgg.length <= 44) withEgg else pick
        lastKey = key
        return cachedTitle
    }

    private fun maybeEgg(name: String): String? {
        if (Math.random() > 0.30) return null
        rollDay()
        val candidates = mutableListOf<String>()
        if (bubblesToday > 0) candidates.add("今天已经帮你弹了 " + bubblesToday + " 个气泡 💨")
        val mins = (System.currentTimeMillis() - startedAt) / 60_000L
        if (mins >= 30) candidates.add("我已经守了 " + (mins / 60) + " 小时 " + (mins % 60) + " 分")
        if (lastMessageAt > 0) {
            val ago = (System.currentTimeMillis() - lastMessageAt) / 60_000L
            if (ago in 5..600) candidates.add("上次有人来找你是 " + ago + " 分钟前")
        }
        if (candidates.isEmpty()) return null
        return candidates.random()
    }

    private fun poolFor(hour: Int, name: String): List<String> {
        val n = name.ifBlank { "我" }
        return when (hour) {
            in 5..10 -> listOf(
                "早上好，" + n + " ☀️",
                "早安，" + n + "，今天也要开心呀",
                n + "，起床啦，我在门口等着 🌅",
                "早上好，" + n + "，有事尽管喊我",
                "天亮啦，" + n + "，我睡了一觉（其实没有）👀",
                n + " 早呀，今天想给谁发点好玩的？",
                "早上好，" + n + "，随时准备出发 🚀",
                n + "，早安，记得吃早饭 🥐",
            )
            in 11..13 -> listOf(
                "中午好，" + n + " 🍚",
                "该吃饭了，" + n + "，别光看手机",
                n + "，中午好呀，吃饱了才有力气发消息",
                "中午好，" + n + "，我替你盯着呢 👀",
                n + "，午安，记得喝口水 💧",
                "中午好，" + n + "，消息我一条都没漏",
                n + "，午休一会儿吧，有消息我会叫你",
            )
            in 14..17 -> listOf(
                "下午好，" + n + " ☕",
                n + "，下午好，撑住，快下班了",
                "下午好，" + n + "，喝口水再继续",
                n + "，我还在线上，很乖 📡",
                "下午好，" + n + "，摸鱼时间到了吗？",
                n + "，困了的话我陪你发呆 😴",
                "下午好，" + n + "，一切正常运转中 ⚙️",
            )
            in 18..22 -> listOf(
                "晚上好，" + n + " 🌙",
                "晚上好，" + n + "，天天开心",
                n + "，晚上好呀，今天过得怎么样？",
                "晚上好，" + n + "，我还在守着 📡",
                n + "，忙完了吗？我一直都在",
                "晚上好，" + n + "，今天也辛苦啦",
                n + "，我今晚值班，你放心睡 🌙",
            )
            else -> listOf(
                "夜深了，" + n + " 🌙",
                n + "，早点睡吧",
                "深夜好，" + n + "，我守着就行",
                n + "，世界安静下来了",
                "夜深了，" + n + "，有消息我会轻轻叫你",
                n + "，我在，你安心休息 🌙",
            )
        }
    }
}
