package com.example.ntfypop.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json

class NtfyClient(val serverUrl: String) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val plain = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 发布一条信封（走 ntfy 的 JSON 发布：POST 根路径，topic/title 都在 body 里）。 */
    suspend fun publishEnvelope(topic: String, envelopeJson: String, title: String, priority: Int = 3): String? =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(
                NtfyPublish.serializer(),
                NtfyPublish(topic = topic, message = envelopeJson, title = title, priority = priority),
            )
            val req = Request.Builder()
                .url(serverUrl.trimEnd('/') + "/")
                .post(payload.toRequestBody(JSON_TYPE))
                .build()
            plain.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("publish HTTP " + resp.code + " " + body.take(200))
                }
                parseServerId(body)
            }
        }

    /**
     * 两段式发布的第一段：把文件 PUT 到 topic 上，拿到服务端消息 id 与附件 URL。
     * 头里的 Title 固定为 __blob__，接收端看到它就忽略这条原始消息。
     */
    suspend fun uploadFile(topic: String, bytes: ByteArray, filename: String, mime: String): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url(topic))
                .put(bytes.toRequestBody(mime.toMediaType()))
                .header("Filename", filename)
                .header("Title", NtfyMessage.TITLE_BLOB)
                .header("Priority", "1")
                .build()
            plain.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("upload HTTP " + resp.code + " " + body.take(200))
                val msg = try {
                    json.decodeFromString(NtfyMessage.serializer(), body)
                } catch (e: Exception) {
                    null
                }
                Pair(msg?.id, msg?.attachment?.url)
            }
        }

    /** 下载附件到本地文件（硬性 15MB 上限）。 */
    suspend fun download(url: String, dest: java.io.File): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get().build()
            plain.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body ?: return@withContext false
                if (body.contentLength() > MAX_DOWNLOAD_BYTES) return@withContext false
                dest.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun url(topic: String): String = serverUrl.trimEnd('/') + "/" + topic

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_DOWNLOAD_BYTES = 15L * 1024 * 1024
        internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parseServerId(respBody: String): String? = try {
            json.decodeFromString(NtfyMessage.serializer(), respBody).id.ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
