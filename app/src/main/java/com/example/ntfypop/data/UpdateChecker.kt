package com.example.ntfypop.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.ntfypop.core.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val notes: String = "",
)

/**
 * 应用内自更新：
 * 1) 读取仓库里的 docs/version.json（多路兜底：GitHub 直连 → gh-proxy 镜像 → jsDelivr）
 * 2) 与本机 versionCode 比较，更新的才提示
 * 3) 下载 APK 后调起系统安装器覆盖安装（必须同一签名密钥）
 */
object UpdateChecker {

    private const val OWNER_REPO = "Heibuling/Heibuling"

    /** 版本信息的多路来源（国内直连 GitHub 不稳，所以按顺序兜底）。 */
    private val versionUrls = listOf(
        "https://raw.githubusercontent.com/" + OWNER_REPO + "/main/docs/version.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/" + OWNER_REPO + "/main/docs/version.json",
        "https://ghproxy.net/https://raw.githubusercontent.com/" + OWNER_REPO + "/main/docs/version.json",
        "https://cdn.jsdelivr.net/gh/" + OWNER_REPO + "@main/docs/version.json",
    )

    /** APK 下载镜像前缀（直连慢/被断时依次兜底）。 */
    private val mirrorPrefixes = listOf(
        "",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://ghfast.top/",
    )

    /** 下载用的客户端：读超时长一些，慢链路更容易撑住。 */
    private val downloadHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 本机版本号（API 28+ 用 longVersionCode）。 */
    fun currentVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (e: Exception) {
        0
    }

    fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** 查询最新版本；查不到或已是最新返回 null。 */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = Json { ignoreUnknownKeys = true }
        for (url in versionUrls) {
            try {
                val req = Request.Builder().url(url).get().build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank() || !body.trimStart().startsWith("{")) return@use
                    val info = json.decodeFromString(UpdateInfo.serializer(), body)
                    if (info.versionCode > currentVersionCode(context)) return@withContext info
                    return@withContext null
                }
            } catch (e: Exception) {
                // 换下一个源
            }
        }
        null
    }

    /**
     * 下载 APK：**断点续传 + 多镜像 + 同源重试**。
     * 慢链路（实测约 100KB/s）常常在 99% 处被断开，重头下载几乎不可能成功，
     * 所以每次重试都尽量带 Range 从已下载的位置继续。
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val dest = File(dir, "Heibuling-" + info.versionName + ".apk")

        var lastError: Exception? = null
        for (prefix in mirrorPrefixes) {
            val url = prefix + info.apkUrl
            // 同一来源最多试 3 次，每次都尽量续传
            for (attempt in 1..3) {
                try {
                    var start = if (dest.exists()) dest.length() else 0L
                    val builder = Request.Builder().url(url).get()
                    if (start > 0) builder.header("Range", "bytes=" + start + "-")
                    downloadHttp.newCall(builder.build()).execute().use { resp ->
                        val resuming = resp.code == 206
                        if (resp.code == 416) {
                            // 已经下完了
                            if (dest.length() > 100 * 1024) return@withContext dest
                            start = 0L
                        } else if (!resp.isSuccessful) {
                            throw IOException("HTTP " + resp.code)
                        }
                        if (!resuming) {
                            start = 0L
                            if (dest.exists()) dest.delete()
                        }
                        val body = resp.body ?: throw IOException("空响应")
                        val chunk = body.contentLength()
                        val total = if (chunk > 0) chunk + start else -1L
                        body.byteStream().use { input ->
                            java.io.FileOutputStream(dest, resuming).use { output ->
                                val buf = ByteArray(64 * 1024)
                                var sum = start
                                var read = input.read(buf)
                                while (read > 0) {
                                    output.write(buf, 0, read)
                                    sum += read
                                    onProgress(sum, if (total > 0) total else sum)
                                    read = input.read(buf)
                                }
                            }
                        }
                    }
                    if (dest.length() > 100 * 1024 && (info.apkUrl.isEmpty() || true)) {
                        return@withContext dest
                    }
                    throw IOException("下载不完整")
                } catch (e: Exception) {
                    lastError = e
                    // 注意：这里**不删除**已下载内容，下一次重试才能续传
                }
            }
        }
        throw lastError ?: IOException("所有下载源都失败了")
    }

    /** 是否有"安装未知应用"的权限；没有则跳设置页。 */
    fun canInstall(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 26) context.packageManager.canRequestPackageInstalls() else true
    } catch (e: Exception) {
        true
    }

    fun openInstallPermissionSetting(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
            }
        }
    }

    /** 调起系统安装器覆盖安装。 */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
