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
        "https://cdn.jsdelivr.net/gh/" + OWNER_REPO + "@main/docs/version.json",
    )

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

    /** 下载 APK（失败时自动走 gh-proxy 镜像）。返回本地文件。 */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val dest = File(dir, "Heibuling-" + info.versionName + ".apk")
        if (dest.exists()) dest.delete()

        val attempts = listOf(
            info.apkUrl,
            "https://gh-proxy.com/" + info.apkUrl,
        )
        var lastError: Exception? = null
        for (url in attempts) {
            try {
                val req = Request.Builder().url(url).get().build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP " + resp.code)
                    val body = resp.body ?: throw IOException("空响应")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        dest.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            var sum = 0L
                            while (true) {
                                read = input.read(buf)
                                if (read <= 0) break
                                output.write(buf, 0, read)
                                sum += read
                                if (total > 0) onProgress((sum.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (dest.length() < 1024 * 100) throw IOException("下载不完整")
                return@withContext dest
            } catch (e: Exception) {
                lastError = e
                if (dest.exists()) dest.delete()
            }
        }
        throw lastError ?: IOException("下载失败")
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
