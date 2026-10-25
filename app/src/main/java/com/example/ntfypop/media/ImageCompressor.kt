package com.example.ntfypop.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 图片压缩：长边不超过 1600px、JPEG 质量自适应，保证不超过 1.5MB。 */
object ImageCompressor {

    data class Result(val bytes: ByteArray, val width: Int, val height: Int)

    suspend fun compress(context: Context, uri: Uri): Result? = withContext(Dispatchers.IO) {
        try {
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext null
            val edge = max(original.width, original.height)
            val scaled = if (edge > MAX_EDGE) {
                val s = MAX_EDGE.toFloat() / edge
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * s).roundToInt(),
                    (original.height * s).roundToInt(),
                    true,
                )
            } else {
                original
            }
            var quality = 85
            var out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            while (out.size() > MAX_BYTES && quality > 40) {
                quality -= 10
                out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            Result(out.toByteArray(), scaled.width, scaled.height)
        } catch (e: Exception) {
            null
        }
    }

    private const val MAX_EDGE = 1600
    private const val MAX_BYTES = 1_500_000
}
