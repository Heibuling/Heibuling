package com.example.ntfypop.data

import com.example.ntfypop.data.local.MessageRec
import com.example.ntfypop.data.local.Peer
import com.example.ntfypop.data.local.Store
import com.example.ntfypop.data.prefs.SettingsStore
import com.example.ntfypop.data.remote.NtfyClient
import com.example.ntfypop.domain.Envelope
import com.example.ntfypop.domain.EnvelopeCodec

class ChatRepo(
    private val store: Store,
    private val settings: SettingsStore,
) {
    /** 发送一条文字/emoji 消息：先落库（sending），发布成功后改 sent，失败改 failed。 */
    suspend fun sendText(peer: Peer, text: String, kind: String = Envelope.KIND_TEXT): MessageRec {
        val self = settings.ensureInitialized()
        val mid = EnvelopeCodec.newMid()
        val rec = MessageRec(
            mid = mid,
            peerUid = peer.uid,
            outgoing = true,
            kind = kind,
            text = text,
            ts = System.currentTimeMillis(),
            state = MessageRec.STATE_SENDING,
        )
        store.addMessage(rec)
        try {
            val env = Envelope(
                k = Envelope.KIND_MSG,
                mid = mid,
                from = self.uid,
                name = self.name,
                to = peer.uid,
                ts = rec.ts,
                kind = kind,
                text = text,
            )
            NtfyClient(self.serverUrl).publishEnvelope(
                peer.topic,
                EnvelopeCodec.encode(env),
                self.name,
                3,
            )
            store.updateMessageState(mid, MessageRec.STATE_SENT)
        } catch (e: Exception) {
            store.updateMessageState(mid, MessageRec.STATE_FAILED)
            throw e
        }
        return rec
    }

    /**
     * 发送图片/表情包：两段式发布。
     * 第一段 PUT 上传文件拿到附件 URL，第二段 POST 信封引用它。
     */
    suspend fun sendImage(peer: Peer, bytes: ByteArray, filename: String, mime: String): MessageRec {
        val self = settings.ensureInitialized()
        val mid = EnvelopeCodec.newMid()
        val rec = MessageRec(
            mid = mid,
            peerUid = peer.uid,
            outgoing = true,
            kind = Envelope.KIND_IMAGE,
            text = "[图片]",
            ts = System.currentTimeMillis(),
            state = MessageRec.STATE_SENDING,
        )
        store.addMessage(rec)
        try {
            val client = NtfyClient(self.serverUrl)
            val (blobId, blobUrl) = client.uploadFile(peer.topic, bytes, filename, mime)
            if (blobUrl.isNullOrBlank()) throw IllegalStateException("附件上传成功但没拿到 URL")
            val env = Envelope(
                k = Envelope.KIND_MSG,
                mid = mid,
                from = self.uid,
                name = self.name,
                to = peer.uid,
                ts = rec.ts,
                kind = Envelope.KIND_IMAGE,
                text = "",
                blob = com.example.ntfypop.domain.Blob(
                    url = blobUrl,
                    name = filename,
                    mime = mime,
                    size = bytes.size.toLong(),
                ),
                ref = blobId.orEmpty(),
            )
            client.publishEnvelope(peer.topic, EnvelopeCodec.encode(env), self.name, 3)
            store.updateMessageState(mid, MessageRec.STATE_SENT)
        } catch (e: Exception) {
            store.updateMessageState(mid, MessageRec.STATE_FAILED)
            throw e
        }
        return rec
    }

    /** 发送语音：两段式发布（先上传 m4a，再发信封带上时长与波形）。 */
    suspend fun sendVoice(peer: Peer, file: java.io.File, durationMs: Long, peaks: List<Float>): MessageRec {
        val self = settings.ensureInitialized()
        val mid = EnvelopeCodec.newMid()
        val rec = MessageRec(
            mid = mid,
            peerUid = peer.uid,
            outgoing = true,
            kind = Envelope.KIND_VOICE,
            text = "[语音]",
            ts = System.currentTimeMillis(),
            state = MessageRec.STATE_SENDING,
        )
        store.addMessage(rec)
        try {
            val bytes = file.readBytes()
            val client = NtfyClient(self.serverUrl)
            val (blobId, blobUrl) = client.uploadFile(peer.topic, bytes, file.name, "audio/mp4")
            if (blobUrl.isNullOrBlank()) throw IllegalStateException("语音上传成功但没拿到 URL")
            val env = Envelope(
                k = Envelope.KIND_MSG,
                mid = mid,
                from = self.uid,
                name = self.name,
                to = peer.uid,
                ts = rec.ts,
                kind = Envelope.KIND_VOICE,
                text = "",
                blob = com.example.ntfypop.domain.Blob(
                    url = blobUrl,
                    name = file.name,
                    mime = "audio/mp4",
                    size = bytes.size.toLong(),
                    dur = durationMs,
                    peaks = peaks,
                ),
                ref = blobId.orEmpty(),
            )
            client.publishEnvelope(peer.topic, EnvelopeCodec.encode(env), self.name, 3)
            store.updateMessageState(mid, MessageRec.STATE_SENT)
        } catch (e: Exception) {
            store.updateMessageState(mid, MessageRec.STATE_FAILED)
            throw e
        }
        return rec
    }

    /** 发送涂鸦：体积小就内联在信封里，超过 3.5KB 则上传成 .json 附件。 */
    suspend fun sendDoodle(
        peer: Peer,
        strokes: List<com.example.ntfypop.media.DoodleStroke>,
        canvasW: Int = 0,
        canvasH: Int = 0,
    ): MessageRec {
        val self = settings.ensureInitialized()
        val mid = EnvelopeCodec.newMid()
        val rec = MessageRec(
            mid = mid,
            peerUid = peer.uid,
            outgoing = true,
            kind = Envelope.KIND_DOODLE,
            text = "[涂鸦]",
            ts = System.currentTimeMillis(),
            state = MessageRec.STATE_SENDING,
        )
        store.addMessage(rec)
        try {
            val json = com.example.ntfypop.media.DoodleCodec.encode(strokes)
            val client = NtfyClient(self.serverUrl)
            var inline = json
            var blob: com.example.ntfypop.domain.Blob? = null
            var ref = ""
            if (json.toByteArray(Charsets.UTF_8).size > com.example.ntfypop.media.DoodleCodec.INLINE_MAX_BYTES) {
                val bytes = json.toByteArray(Charsets.UTF_8)
                val (blobId, blobUrl) = client.uploadFile(peer.topic, bytes, "doodle_" + mid + ".json", "application/json")
                if (blobUrl.isNullOrBlank()) throw IllegalStateException("涂鸦上传成功但没拿到 URL")
                blob = com.example.ntfypop.domain.Blob(
                    url = blobUrl,
                    name = "doodle_" + mid + ".json",
                    mime = "application/json",
                    size = bytes.size.toLong(),
                )
                ref = blobId.orEmpty()
                inline = ""
            }
            val env = Envelope(
                k = Envelope.KIND_MSG,
                mid = mid,
                from = self.uid,
                name = self.name,
                to = peer.uid,
                ts = rec.ts,
                kind = Envelope.KIND_DOODLE,
                text = "",
                strokes = inline,
                doodleW = canvasW,
                doodleH = canvasH,
                blob = blob,
                ref = ref,
            )
            client.publishEnvelope(peer.topic, EnvelopeCodec.encode(env), self.name, 3)
            store.updateMessageState(mid, MessageRec.STATE_SENT)
        } catch (e: Exception) {
            store.updateMessageState(mid, MessageRec.STATE_FAILED)
            throw e
        }
        return rec
    }

    /** 配对成功后向该 topic 宣告自己（让对方自动建立好友项）。 */
    suspend fun sayHello(serverUrl: String, topic: String): Boolean {
        val self = settings.ensureInitialized()
        return try {
            val env = Envelope(
                k = Envelope.KIND_HELLO,
                mid = EnvelopeCodec.newMid(),
                from = self.uid,
                name = self.name,
                to = "",
                ts = System.currentTimeMillis(),
                kind = Envelope.KIND_TEXT,
                text = "",
            )
            NtfyClient(serverUrl).publishEnvelope(topic, EnvelopeCodec.encode(env), self.name, 3)
            true
        } catch (e: Exception) {
            false
        }
    }
}
