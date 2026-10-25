package com.example.ntfypop.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * 语音录制：AAC-LC / MPEG_4 / 单声道 / 16kHz / 32kbps，最长 20 秒自动停止。
 * 录制过程中每 50ms 采一次振幅，结束时归并成 32 个 0..1 的峰值用于画波形。
 */
object VoiceRecorder {

    data class Result(val file: File, val durationMs: Long, val peaks: List<Float>)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt = 0L
    private var timer: Job? = null
    private val samples = mutableListOf<Float>()

    var onAutoStop: (() -> Unit)? = null
    var onTick: ((Long) -> Unit)? = null

    fun isRecording(): Boolean = recorder != null

    fun elapsedMs(): Long = if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt

    fun start(context: Context): Boolean {
        if (recorder != null) return false
        return try {
            val dir = File(context.filesDir, "voice").apply { mkdirs() }
            val f = File(dir, "rec_" + System.currentTimeMillis() + ".m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(32000)
            r.setAudioChannels(1)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            target = f
            startedAt = System.currentTimeMillis()
            samples.clear()

            timer?.cancel()
            timer = scope.launch {
                while (isActive && recorder != null) {
                    delay(50)
                    val amp = try {
                        recorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    samples.add((amp / 32767f).coerceIn(0f, 1f))
                    val e = elapsedMs()
                    onTick?.invoke(e)
                    if (e >= MAX_MS) {
                        onAutoStop?.invoke()
                        break
                    }
                }
            }
            true
        } catch (e: Exception) {
            cancel()
            false
        }
    }

    /** 停止并返回结果；没录到内容返回 null。 */
    fun stop(): Result? {
        val r = recorder ?: return null
        val f = target
        val dur = elapsedMs()
        recorder = null
        target = null
        startedAt = 0L
        timer?.cancel()
        timer = null
        return try {
            r.stop()
            r.release()
            if (f == null || !f.exists() || f.length() < 1024L) {
                f?.delete()
                null
            } else {
                Result(f, dur, downsample(samples.toList(), 32))
            }
        } catch (e: Exception) {
            try {
                r.release()
            } catch (ignored: Exception) {
            }
            f?.delete()
            null
        }
    }

    fun cancel() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
        }
        recorder = null
        target?.delete()
        target = null
        startedAt = 0L
        timer?.cancel()
        timer = null
        samples.clear()
    }

    private fun downsample(values: List<Float>, n: Int): List<Float> {
        if (values.isEmpty()) return List(n) { 0.08f }
        if (values.size <= n) {
            val padded = values + List(n - values.size) { values.last() }
            return padded.map { it.coerceAtLeast(0.08f) }
        }
        val chunk = values.size / n
        return (0 until n).map { i ->
            var m = 0f
            for (j in i * chunk until max((i + 1) * chunk, i * chunk + 1)) {
                if (j < values.size) m = max(m, abs(values[j]))
            }
            m.coerceAtLeast(0.08f)
        }
    }

    const val MAX_MS = 20_000L
}
