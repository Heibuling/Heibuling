package com.example.ntfypop.domain

import com.example.ntfypop.core.Constants
import kotlinx.serialization.json.Json
import java.security.SecureRandom

object EnvelopeCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private val rnd = SecureRandom()

    fun encode(e: Envelope): String = json.encodeToString(Envelope.serializer(), e)

    /** 宽容解析：不是我们的信封就返回 null，绝不抛异常。 */
    fun decode(raw: String): Envelope? {
        val t = raw.trim()
        if (t.length < 2 || t[0] != '{' || !t.contains("\"v\"")) return null
        return try {
            json.decodeFromString(Envelope.serializer(), t)
        } catch (e: Exception) {
            null
        }
    }

    fun newMid(): String = "c-" + randomCrockford(8).lowercase()

    fun randomCrockford(len: Int): String {
        val sb = StringBuilder(len)
        repeat(len) { sb.append(Constants.CROCKFORD[rnd.nextInt(Constants.CROCKFORD.length)]) }
        return sb.toString()
    }

    fun newUid(): String = "u_" + randomCrockford(8).lowercase()
}
