package com.example.ntfypop.data.remote

import android.util.Base64
import com.example.ntfypop.core.Constants
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 邀请码：NTP1- + Base64URL(紧凑 JSON)。
 * 载荷里带服务器地址、topic、创建者 uid 与昵称。
 */
object Pairing {

    @Serializable
    data class Invite(
        val v: Int = 1,
        val sv: String,
        val tp: String,
        val uid: String,
        val nm: String,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(invite: Invite): String {
        val raw = json.encodeToString(Invite.serializer(), invite)
        val b64 = Base64.encodeToString(
            raw.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return Constants.INVITE_PREFIX + b64
    }

    /** 宽容解析：允许空白/换行、允许缺少前缀、允许微信插入空格。 */
    fun decode(input: String): Invite? {
        val cleaned = input.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return null
        val body = if (cleaned.startsWith(Constants.INVITE_PREFIX)) {
            cleaned.removePrefix(Constants.INVITE_PREFIX)
        } else {
            cleaned
        }
        return try {
            val bytes = Base64.decode(body, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            json.decodeFromString(Invite.serializer(), String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}
