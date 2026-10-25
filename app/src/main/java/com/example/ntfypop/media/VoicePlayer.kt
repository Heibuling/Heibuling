package com.example.ntfypop.media

import android.media.MediaPlayer

/** 极简语音播放器：同时只允许播放一条。 */
object VoicePlayer {

    private var player: MediaPlayer? = null

    fun isPlaying(path: String?): Boolean = player != null && path != null

    fun play(path: String, onDone: () -> Unit) {
        stop()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.prepare()
            mp.start()
            mp.setOnCompletionListener {
                stop()
                onDone()
            }
            player = mp
        } catch (e: Exception) {
            stop()
            onDone()
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }
}
