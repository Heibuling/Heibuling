package com.example.ntfypop.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.ntfypop.core.Constants
import com.example.ntfypop.data.local.MessageRec
import com.example.ntfypop.data.local.Peer
import com.example.ntfypop.data.local.Store
import com.example.ntfypop.data.prefs.Identity
import com.example.ntfypop.data.prefs.SettingsStore
import com.example.ntfypop.data.remote.NtfyClient
import com.example.ntfypop.data.remote.NtfyMessage
import com.example.ntfypop.data.remote.NtfyStream
import com.example.ntfypop.domain.Envelope
import com.example.ntfypop.domain.EnvelopeCodec
import com.example.ntfypop.service.overlay.OverlayController
import com.example.ntfypop.service.DiagState
import com.example.ntfypop.service.overlay.OverlayItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.min

class LinkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: SettingsStore
    private lateinit var store: Store
    private var loopJob: Job? = null
    private val topicJobs = mutableMapOf<String, Job>()
    private val clients = mutableMapOf<String, NtfyClient>()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(applicationContext)
        store = Store.get(applicationContext)
        // 必须在 5 秒内完成 startForeground
        val notif = NotificationFactory.starting(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Constants.NOTIF_ID_PERSISTENT, notif, type)
        NotificationFactory.onServiceStarted()
        // 立刻用真实昵称刷新一次通知（不要用占位名，否则会污染问候语缓存）
        scope.launch {
            try {
                val id = settings.ensureInitialized()
                updateNotification(id.name, false)
            } catch (e: Exception) {
            }
        }
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 为「开发 topic + 所有好友 topic」各维持一条订阅，好友列表变化时自动增删。 */
    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            store.load()
            val id = settings.ensureInitialized()
            launch {
                // 订阅集合 = 开发 topic + 已有好友的 topic + **待对方加入的邀请 topic**。
                // 最后一项很关键：不订阅邀请 topic，朋友的 hello 就永远收不到，配对无法完成。
                combine(store.peers, store.pending) { peers, pending -> peers to pending }
                    .collect { (peers, pending) ->
                        val topics = linkedSetOf(id.topic)
                        peers.forEach { if (it.topic.isNotBlank()) topics.add(it.topic) }
                        pending.forEach { if (it.topic.isNotBlank()) topics.add(it.topic) }
                        topicJobs.keys.toList().filter { it !in topics }.forEach { old ->
                            topicJobs.remove(old)?.cancel()
                            Log.i(Constants.LOG_TAG, "取消订阅 " + maskTopic(old))
                        }
                        topics.filter { it !in topicJobs }.forEach { topic ->
                            Log.i(Constants.LOG_TAG, "新增订阅 " + maskTopic(topic))
                            topicJobs[topic] = scope.launch { runTopic(topic, id) }
                        }
                    }
            }
            while (isActive) delay(30_000)
        }
    }

    private suspend fun runTopic(topic: String, id: Identity) {
        var attempt = 0
        while (true) {
            val client = clients.getOrPut(id.serverUrl) { NtfyClient(id.serverUrl) }
            // 关键修复：没有 lastServerId 时（刚配对出来的新 topic）不能只订阅"实时"，
            // 否则 App 不在运行时对方发的消息/hello 永远补不回来。这里退回拉取最近 1 小时。
            val since = store.lastServerId(topic) ?: "1h"
            try {
                updateNotification(id.name, true)
                Log.i(Constants.LOG_TAG, "订阅开始 " + maskTopic(topic) + " uid=" + id.uid)
                DiagState.log("订阅 " + maskTopic(topic) + "（since=" + since + "）")
                DiagState.update(topic) { it.copy(connected = false, attempts = attempt, lastError = "") }
                NtfyStream(client.http)
                    .open(id.serverUrl, topic, since)
                    .flowOn(Dispatchers.IO)
                    .collect { msg ->
                        // 只有真实消息事件才存 id：keepalive/open 事件的 id 不是消息 id，
                        // 用它做 since= 会让服务端回放旧消息（实测踩过这个坑）
                        if (msg.event == NtfyMessage.EVENT_MESSAGE && msg.id.isNotBlank()) {
                            store.setLastServerId(topic, msg.id)
                        }
                        attempt = 0
                        DiagState.update(topic) {
                            it.copy(
                                connected = it.connected || msg.event == NtfyMessage.EVENT_OPEN ||
                                    msg.event == NtfyMessage.EVENT_KEEPALIVE,
                                attempts = 0,
                                lastEventAt = System.currentTimeMillis(),
                                lastEvent = msg.event,
                                lastServerId = if (msg.event == NtfyMessage.EVENT_MESSAGE) msg.id else it.lastServerId,
                            )
                        }
                        if (msg.event == NtfyMessage.EVENT_MESSAGE) {
                            DiagState.log("收到 " + maskTopic(topic) + " id=" + msg.id)
                        }
                        handle(msg, topic, id, client)
                    }
                throw IOException("stream closed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(Constants.LOG_TAG, "连接中断 " + maskTopic(topic) + ": " + e.message)
                DiagState.log("断开 " + maskTopic(topic) + "：" + e.message)
                DiagState.update(topic) {
                    it.copy(connected = false, attempts = attempt + 1, lastError = e.message.orEmpty())
                }
                updateNotification(id.name, false)
                val backoff = Constants.RECONNECT_BACKOFF_MS[min(attempt, Constants.RECONNECT_BACKOFF_MS.size - 1)]
                attempt++
                delay(backoff)
            }
        }
    }

    private fun handle(msg: NtfyMessage, topic: String, id: Identity, client: NtfyClient) {
        when (msg.event) {
            NtfyMessage.EVENT_KEEPALIVE -> return
            NtfyMessage.EVENT_OPEN -> {
                Log.i(Constants.LOG_TAG, "流已打开 " + maskTopic(msg.topic))
                DiagState.update(topic) { it.copy(connected = true) }
                return
            }
            NtfyMessage.EVENT_MESSAGE -> Unit
            else -> return
        }
        if (msg.title == NtfyMessage.TITLE_BLOB) {
            Log.i(Constants.LOG_TAG, "忽略附件原始消息 " + msg.id)
            return
        }
        val env = EnvelopeCodec.decode(msg.message)
        if (env == null) {
            Log.i(Constants.LOG_TAG, "非信封正文，忽略（" + msg.message.length + " 字）")
            return
        }
        if (env.from == id.uid) return // 回显过滤

        when (env.k) {
            Envelope.KIND_HELLO -> {
                Log.i(Constants.LOG_TAG, "收到 hello，建立好友: " + env.name)
                scope.launch {
                    store.removePendingInvite(topic)
                    store.upsertPeer(
                        Peer(
                            uid = env.from,
                            topic = topic,
                            remoteName = env.name,
                            createdAt = System.currentTimeMillis(),
                            lastSeenAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }

            Envelope.KIND_MSG -> {
                Log.i(Constants.LOG_TAG, "收到消息 kind=" + env.kind + " from=" + env.name)
                NotificationFactory.onMessageReceived()
                scope.launch {
                    store.upsertPeer(
                        Peer(
                            uid = env.from,
                            topic = topic,
                            remoteName = env.name,
                            createdAt = System.currentTimeMillis(),
                            lastSeenAt = System.currentTimeMillis(),
                        ),
                    )
                    val isImage = env.kind == Envelope.KIND_IMAGE || env.kind == Envelope.KIND_STICKER
                    val isVoice = env.kind == Envelope.KIND_VOICE
                    val isDoodle = env.kind == Envelope.KIND_DOODLE
                    val inlineDoodle = if (isDoodle) {
                        com.example.ntfypop.media.DoodleCodec.decode(env.strokes)
                    } else {
                        emptyList()
                    }
                    val blobUrl = env.blob?.url.orEmpty()
                    val hasBlob = blobUrl.isNotBlank() && (isImage || isVoice || isDoodle)

                    store.addMessage(
                        MessageRec(
                            mid = env.mid,
                            peerUid = env.from,
                            outgoing = false,
                            kind = env.kind,
                            text = when {
                                isImage -> "[图片]"
                                isVoice -> "[语音]"
                                isDoodle -> "[涂鸦]"
                                else -> env.text
                            },
                            ts = env.ts,
                            state = MessageRec.STATE_DELIVERED,
                        ),
                    )

                    if (!OverlayController.canDraw(applicationContext)) {
                        NotificationFactory.postFallback(
                            applicationContext,
                            env.name.ifBlank { "朋友" },
                            when {
                                isImage -> "[图片]"
                                isVoice -> "[语音]"
                                isDoodle -> "[涂鸦]"
                                else -> env.text.ifBlank { "（非文字消息）" }
                            },
                        )
                        return@launch
                    }

                    // 先弹框（图片显示占位），避免等待下载造成 4 秒以上的延迟
                    OverlayController.enqueue(
                        applicationContext,
                        OverlayItem(
                            id = env.mid,
                            sender = env.name.ifBlank { "朋友" },
                            text = when {
                                isImage -> "正在接收图片…"
                                isVoice -> "正在接收语音…"
                                isDoodle -> if (inlineDoodle.isEmpty()) "正在接收涂鸦…" else ""
                                else -> env.text
                            },
                            imagePath = null,
                            voiceDurationMs = env.blob?.dur ?: 0L,
                            voicePeaks = env.blob?.peaks ?: emptyList(),
                            doodleStrokes = inlineDoodle,
                            // 语音留 20 秒（够点播放），图片/涂鸦 9 秒，文字 5 秒
                            durationMs = when {
                                isVoice -> Constants.VOICE_BUBBLE_DURATION_MS
                                isImage || isDoodle -> Constants.MEDIA_BUBBLE_DURATION_MS
                                else -> Constants.BUBBLE_DURATION_MS
                            },
                            onShown = {
                                NotificationFactory.onBubbleShown()
                                sendAck(client, topic, id.uid, env, Envelope.STATE_DELIVERED)
                            },
                            onClosed = { manual ->
                                Log.i(Constants.LOG_TAG, "气泡关闭 manual=" + manual)
                                if (manual) sendAck(client, topic, id.uid, env, Envelope.STATE_DISMISSED)
                            },
                        ),
                    )

                    // 再下载附件，成功就原地替换（气泡 5 秒内下载不完也没关系，历史里仍有记录）
                    if (hasBlob) {
                        val dir = java.io.File(applicationContext.filesDir, "media").apply { mkdirs() }
                        val ext = when {
                            isVoice -> ".m4a"
                            isDoodle -> ".json"
                            else -> ".jpg"
                        }
                        val dest = java.io.File(dir, env.mid + ext)
                        val ok = client.download(blobUrl, dest)
                        Log.i(Constants.LOG_TAG, "附件下载 ok=" + ok + " url=" + blobUrl.take(40))
                        if (ok) {
                            if (isVoice) {
                                OverlayController.attachVoice(
                                    env.mid,
                                    dest.absolutePath,
                                    env.blob?.dur ?: 0L,
                                    env.blob?.peaks ?: emptyList(),
                                )
                            } else if (isDoodle) {
                                val text = try {
                                    dest.readText()
                                } catch (e: Exception) {
                                    ""
                                }
                                OverlayController.attachDoodle(
                                    env.mid,
                                    com.example.ntfypop.media.DoodleCodec.decode(text),
                                )
                            } else {
                                OverlayController.attachImage(env.mid, dest.absolutePath)
                            }
                        } else {
                            OverlayController.failMedia(
                                env.mid,
                                when {
                                    isVoice -> "（语音已过期或下载失败）"
                                    isDoodle -> "（涂鸦已过期或下载失败）"
                                    else -> "（图片已过期或下载失败）"
                                },
                            )
                        }
                    }
                }
            }

            Envelope.KIND_ACK -> {
                Log.i(Constants.LOG_TAG, "收到回执 state=" + env.state + " mid=" + env.mid)
                scope.launch {
                    val state = if (env.state == Envelope.STATE_DISMISSED) {
                        MessageRec.STATE_DISMISSED
                    } else {
                        MessageRec.STATE_DELIVERED
                    }
                    store.updateMessageState(env.mid, state)
                    if (state == MessageRec.STATE_DISMISSED) {
                        val peerName = store.peers.value.firstOrNull { it.uid == env.from }
                            ?.remoteName?.ifBlank { null } ?: "对方"
                        NotificationFactory.postAck(applicationContext, "「" + peerName + "」点掉了你的消息")
                    }
                }
            }

            else -> Log.i(Constants.LOG_TAG, "未知信封类型 " + env.k)
        }
    }

    private fun sendAck(client: NtfyClient, topic: String, selfUid: String, src: Envelope, state: String) {
        scope.launch {
            try {
                val ack = Envelope(
                    k = Envelope.KIND_ACK,
                    mid = src.mid,
                    from = selfUid,
                    name = "",
                    to = src.from,
                    ts = System.currentTimeMillis(),
                    kind = Envelope.KIND_TEXT,
                    state = state,
                )
                client.publishEnvelope(topic, EnvelopeCodec.encode(ack), "", 1)
                Log.i(Constants.LOG_TAG, "已回执 " + state)
            } catch (e: Exception) {
                Log.w(Constants.LOG_TAG, "回执失败: " + e.message)
            }
        }
    }

    private fun updateNotification(name: String, connected: Boolean) {
        try {
            val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
            nm.notify(Constants.NOTIF_ID_PERSISTENT, NotificationFactory.persistent(this, name, connected))
        } catch (e: Exception) {
            Log.w(Constants.LOG_TAG, "更新通知失败: " + e.message)
        }
    }

    companion object {
        fun start(context: Context) {
            val i = Intent(context, LinkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LinkService::class.java))
        }

        /** 强制重连：重启服务，重连时会用 since=<lastServerId> 把漏掉的消息补回来。 */
        fun restart(context: Context) {
            stop(context)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    start(context)
                } catch (e: Exception) {
                    Log.w(Constants.LOG_TAG, "重启服务失败: " + e.message)
                }
            }, 700)
        }

        private fun maskTopic(topic: String): String =
            if (topic.length <= 6) "***" else topic.substring(0, 6) + "***"
    }
}
