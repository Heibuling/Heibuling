package com.example.ntfypop.data.remote

import kotlinx.serialization.Serializable

/** ntfy 服务端 /<topic>/json 流里的一行 JSON（只取我们关心的字段）。 */
@Serializable
data class NtfyMessage(
    val id: String = "",
    val time: Long = 0L,
    val event: String = "",
    val topic: String = "",
    val message: String = "",
    val title: String = "",
    val priority: Int = 0,
    val attachment: NtfyAttachment? = null,
) {
    companion object {
        const val EVENT_OPEN = "open"
        const val EVENT_MESSAGE = "message"
        const val EVENT_KEEPALIVE = "keepalive"
        const val TITLE_BLOB = "__blob__"
    }
}

/** ntfy 的 JSON 发布体：POST https://ntfy.sh/ ，topic 放在 body 里。
 * 用它而不是自定义 HTTP 头，是因为 HTTP 头只能放 ASCII，中文昵称会直接被 OkHttp 拒绝。 */
@Serializable
data class NtfyPublish(
    val topic: String,
    val message: String,
    val title: String = "",
    val priority: Int = 3,
    val cache: String = "yes",
)
