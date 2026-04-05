package com.audiobalance.app.poc

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

class InternalAudioSource(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun prepare(rawResId: Int): Int {
        release()
        val mp = MediaPlayer.create(context, rawResId)
            ?: throw IllegalStateException("MediaPlayer.create returned null for res=$rawResId")
        mp.isLooping = true
        mediaPlayer = mp
        Log.d("POC", "InternalAudioSource prepared, sessionId=${mp.audioSessionId}")
        return mp.audioSessionId
    }

    fun play() { mediaPlayer?.start() }
    fun pause() { mediaPlayer?.pause() }

    val sessionId: Int get() = mediaPlayer?.audioSessionId ?: AudioManager.ERROR

    fun release() {
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        mediaPlayer = null
    }
}
