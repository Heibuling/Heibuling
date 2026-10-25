package com.example.ntfypop.service.overlay

/** 一条待显示的气泡。Phase 7/8 会扩展图片、语音、涂鸦字段。 */
data class OverlayItem(
    val id: String,
    val sender: String,
    val text: String,
    val imagePath: String? = null,
    val voicePath: String? = null,
    val voiceDurationMs: Long = 0L,
    val voicePeaks: List<Float> = emptyList(),
    val doodleStrokes: List<com.example.ntfypop.media.DoodleStroke> = emptyList(),
    /** 这条气泡停留多久（语音给得更长，因为要留时间点播放） */
    val durationMs: Long = com.example.ntfypop.core.Constants.BUBBLE_DURATION_MS,
    val onShown: () -> Unit,
    val onClosed: (manual: Boolean) -> Unit,
)
