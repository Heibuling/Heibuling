package com.example.ntfypop.media

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.max

/**
 * 一笔：颜色 + 笔宽（相对画布宽的比例）+ 起止时间（毫秒，相对画布打开时刻）+ 归一化坐标。
 * 坐标扁平化成 [x0,y0,x1,y1,...] 以减小体积（一涂鸦通常 3-8KB）。
 */
@Serializable
data class DoodleStroke(
    val c: String,
    val w: Float,
    val t0: Int,
    val t1: Int,
    val p: List<Float>,
)

object DoodleCodec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(DoodleStroke.serializer())

    fun encode(strokes: List<DoodleStroke>): String = json.encodeToString(listSerializer, strokes)

    fun decode(text: String): List<DoodleStroke> = try {
        json.decodeFromString(listSerializer, text)
    } catch (e: Exception) {
        emptyList()
    }

    /** 回放总时长（原始毫秒），上限 2500ms。 */
    fun replayDurationMs(strokes: List<DoodleStroke>): Int {
        val total = strokes.maxOfOrNull { it.t1 } ?: 0
        return max(1, minOf(total, 2500))
    }

    fun rawDurationMs(strokes: List<DoodleStroke>): Int = max(1, strokes.maxOfOrNull { it.t1 } ?: 1)

    /** 超过这个字节数就不走信封内联，改走附件。 */
    const val INLINE_MAX_BYTES = 3500
}
