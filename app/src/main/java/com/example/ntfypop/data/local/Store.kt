package com.example.ntfypop.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Peer(
    val uid: String,
    val topic: String,
    val remoteName: String = "",
    val localAlias: String = "",
    val createdAt: Long = 0L,
    val lastSeenAt: Long = 0L,
)

@Serializable
data class PendingInvite(
    val topic: String,
    val createdAt: Long,
)

@Serializable
data class MessageRec(
    val mid: String,
    val peerUid: String,
    val outgoing: Boolean,
    val kind: String,
    val text: String,
    val ts: Long,
    val state: String = STATE_SENT,
) {
    companion object {
        const val STATE_SENDING = "sending"
        const val STATE_SENT = "sent"
        const val STATE_DELIVERED = "delivered"
        const val STATE_DISMISSED = "dismissed"
        const val STATE_FAILED = "failed"
    }
}

@Serializable
data class Db(
    val peers: List<Peer> = emptyList(),
    val messages: Map<String, List<MessageRec>> = emptyMap(),
    val lastServerId: Map<String, String> = emptyMap(),
    val pending: List<PendingInvite> = emptyList(),
)

/**
 * 轻量本地存储（v1 不用 Room）：一个 db.json，原子写入。
 * 数据量极小（最多 5 个好友、每人最多 100 条），完全够用，且少两个构建期依赖。
 */
class Store private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "db.json")
    private val tmpFile = File(appContext.filesDir, "db.json.tmp")
    private val mutex = Mutex()

    private var db = Db()
    private var loaded = false

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers

    private val _messages = MutableStateFlow<Map<String, List<MessageRec>>>(emptyMap())
    val messages: StateFlow<Map<String, List<MessageRec>>> = _messages

    private val _pending = MutableStateFlow<List<PendingInvite>>(emptyList())
    val pending: StateFlow<List<PendingInvite>> = _pending

    suspend fun load() {
        mutex.withLock {
            if (loaded) return
            db = read()
            loaded = true
            publish()
        }
    }

    private fun read(): Db = try {
        if (file.exists()) {
            json.decodeFromString(Db.serializer(), file.readText())
        } else {
            Db()
        }
    } catch (e: Exception) {
        // 损坏时备份坏文件并回退到空集合，绝不崩溃
        try {
            file.renameTo(File(appContext.filesDir, "db.corrupt." + System.currentTimeMillis() + ".json"))
        } catch (ignored: Exception) {
        }
        Db()
    }

    private suspend fun persist() {
        try {
            tmpFile.writeText(json.encodeToString(Db.serializer(), db))
            if (file.exists()) file.delete()
            tmpFile.renameTo(file)
        } catch (e: Exception) {
            // 磁盘写入失败不影响内存态
        }
    }

    private fun publish() {
        _peers.value = db.peers
        _messages.value = db.messages
        _pending.value = db.pending
    }

    /** hello 幂等：同一个 uid 只更新不新增。 */
    suspend fun upsertPeer(peer: Peer) {
        mutex.withLock {
            val existing = db.peers.firstOrNull { it.uid == peer.uid }
            val next = if (existing == null) {
                db.peers + peer
            } else {
                db.peers.map {
                    if (it.uid == peer.uid) {
                        it.copy(
                            topic = peer.topic.ifBlank { it.topic },
                            remoteName = peer.remoteName.ifBlank { it.remoteName },
                            lastSeenAt = System.currentTimeMillis(),
                        )
                    } else {
                        it
                    }
                }
            }
            db = db.copy(peers = next)
            persist()
            publish()
        }
    }

    suspend fun addPendingInvite(topic: String) {
        mutex.withLock {
            if (db.pending.any { it.topic == topic }) return
            db = db.copy(pending = db.pending + PendingInvite(topic, System.currentTimeMillis()))
            persist()
            publish()
        }
    }

    suspend fun removePendingInvite(topic: String) {
        mutex.withLock {
            if (db.pending.none { it.topic == topic }) return
            db = db.copy(pending = db.pending.filterNot { it.topic == topic })
            persist()
            publish()
        }
    }

    /** 删除好友：连同他的聊天记录一起清掉（用于清理失效配对/测试数据）。 */
    suspend fun removePeerAndMessages(uid: String) {
        mutex.withLock {
            db = db.copy(
                peers = db.peers.filterNot { it.uid == uid },
                messages = db.messages - uid,
            )
            persist()
            publish()
        }
    }

    suspend fun removePeer(uid: String) {
        mutex.withLock {
            db = db.copy(peers = db.peers.filterNot { it.uid == uid })
            persist()
            publish()
        }
    }

    suspend fun addMessage(rec: MessageRec) {
        mutex.withLock {
            if (db.messages[rec.peerUid]?.any { it.mid == rec.mid } == true) return
            val list = (db.messages[rec.peerUid] ?: emptyList()) + rec
            val trimmed = list.sortedBy { it.ts }.takeLast(MAX_MESSAGES_PER_PEER)
            db = db.copy(messages = db.messages + (rec.peerUid to trimmed))
            persist()
            publish()
        }
    }

    suspend fun updateMessageState(mid: String, state: String) {
        mutex.withLock {
            var changed = false
            val next = db.messages.mapValues { (_, list) ->
                list.map { m ->
                    if (m.mid == mid && m.state != state) {
                        changed = true
                        m.copy(state = state)
                    } else {
                        m
                    }
                }
            }
            if (!changed) return
            db = db.copy(messages = next)
            persist()
            publish()
        }
    }

    suspend fun setLastServerId(topic: String, id: String) {
        if (id.isBlank()) return
        mutex.withLock {
            db = db.copy(lastServerId = db.lastServerId + (topic to id))
            persist()
        }
    }

    fun lastServerId(topic: String): String? = db.lastServerId[topic]

    companion object {
        const val MAX_MESSAGES_PER_PEER = 100
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }
}
