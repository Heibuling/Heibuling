package com.example.ntfypop.domain

import kotlinx.serialization.Serializable

/**
 * NtfyPop 自定义信封协议 v1。
 * 我们不改 ntfy 协议，只把 ntfy 的消息正文当作传输层。
 * 任何无法解析成本结构的正文都会被丢弃（例如上传附件时产生的那条原始消息）。
 */
@Serializable
data class Envelope(
    val v: Int = 1,
    val k: String = KIND_MSG,
    val mid: String,
    val from: String,
    val name: String = "",
    val to: String = "",
    val ts: Long,
    val kind: String = KIND_TEXT,
    val text: String = "",
    val blob: Blob? = null,
    val strokes: String = "",
    val ref: String = "",
    val state: String = "",
) {
    companion object {
        const val KIND_MSG = "msg"
        const val KIND_ACK = "ack"
        const val KIND_HELLO = "hello"

        const val KIND_TEXT = "text"
        const val KIND_EMOJI = "emoji"
        const val KIND_STICKER = "sticker"
        const val KIND_IMAGE = "image"
        const val KIND_VOICE = "voice"
        const val KIND_DOODLE = "doodle"

        const val STATE_DELIVERED = "delivered"
        const val STATE_DISMISSED = "dismissed"
    }
}

@Serializable
data class Blob(
    val url: String = "",
    val name: String = "",
    val mime: String = "",
    val size: Long = 0L,
    val w: Int = 0,
    val h: Int = 0,
    val dur: Long = 0L,
    val peaks: List<Float> = emptyList(),
)
