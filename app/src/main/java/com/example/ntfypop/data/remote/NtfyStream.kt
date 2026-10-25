package com.example.ntfypop.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** ntfy 的 /<topic>/json 长连接订阅，逐行产出事件。 */
class NtfyStream(private val http: OkHttpClient) {

    fun open(serverUrl: String, topic: String, since: String?): Flow<NtfyMessage> = flow {
        val base = serverUrl.trimEnd('/') + "/" + topic + "/json"
        val full = if (since.isNullOrBlank()) base else base + "?since=" + since
        val req = Request.Builder().url(full).get().header("Cache", "yes").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("stream HTTP " + resp.code)
            val source = resp.body?.source() ?: throw IOException("stream empty body")
            while (true) {
                val line = try {
                    source.readUtf8Line()
                } catch (e: IOException) {
                    throw e
                } ?: break
                if (line.isBlank()) continue
                val msg = try {
                    json.decodeFromString(NtfyMessage.serializer(), line)
                } catch (e: Exception) {
                    continue
                }
                emit(msg)
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
