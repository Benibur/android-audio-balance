package com.audiobalance.app.poc

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

data class DiscoveredSession(val sessionId: Int, val usage: Int)

object FallbackProbes {

    /**
     * Lists every active playback configuration system-wide. API 26+, no permission needed.
     * Note: AudioPlaybackConfiguration.getAudioSessionId() is a hidden API (@hide in AOSP).
     * We use reflection to attempt access; returns -1 if unavailable on this ROM.
     */
    fun discoverActiveSessions(context: Context): List<DiscoveredSession> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val configs = am.activePlaybackConfigurations
        Log.d("POC", "discoverActiveSessions: found ${configs.size} active configs")
        val list = configs.map { cfg ->
            val id = getAudioSessionIdViaReflection(cfg)
            val usage = cfg.audioAttributes?.usage ?: -1
            Log.d("POC", "Active config: id=$id usage=$usage attrs=${cfg.audioAttributes}")
            DiscoveredSession(id, usage)
        }
        if (list.isEmpty()) Log.d("POC", "discoverActiveSessions: none")
        return list
    }

    /**
     * Attempts to read audioSessionId from AudioPlaybackConfiguration via reflection.
     * getAudioSessionId() is @hide in AOSP but present at runtime on API 26+ devices.
     * Returns -1 if reflection fails (e.g., ROM strips hidden APIs).
     */
    private fun getAudioSessionIdViaReflection(cfg: AudioPlaybackConfiguration): Int {
        return try {
            val method = AudioPlaybackConfiguration::class.java.getDeclaredMethod("getAudioSessionId")
            method.isAccessible = true
            (method.invoke(cfg) as? Int) ?: -1
        } catch (e: Exception) {
            Log.w("POC", "getAudioSessionId reflection FAILED: ${e.javaClass.simpleName}: ${e.message}")
            -1
        }
    }

    /** Attempts DynamicsProcessing on an arbitrary session. Returns the effect (caller owns release) or null. */
    @RequiresApi(Build.VERSION_CODES.P)
    fun tryDynamicsProcessingOnSession(sessionId: Int): DynamicsProcessing? {
        return try {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false, 0,
                false, 0,
                false, 0,
                false
            ).build()
            val dp = DynamicsProcessing(0, sessionId, cfg)
            val enableResult = dp.setEnabled(true)
            Log.d("POC", "DP on session=$sessionId OK enabled=$enableResult hasControl=${dp.hasControl()}")
            dp
        } catch (e: RuntimeException) {
            Log.e("POC", "DP on session=$sessionId FAILED: ${e.message}")
            null
        }
    }

    /** Probe: does ANY effect work on session 0? LoudnessEnhancer is a separate code path. */
    fun tryLoudnessEnhancerSession0(): LoudnessEnhancer? {
        return try {
            val le = LoudnessEnhancer(0)
            val enableResult = le.setEnabled(true)
            Log.d("POC", "LoudnessEnhancer session=0 OK enabled=$enableResult")
            le
        } catch (e: RuntimeException) {
            Log.e("POC", "LoudnessEnhancer session=0 FAILED: ${e.message}")
            null
        }
    }

    /** Send the OPEN broadcast for a session we control, so our own receiver + any system effects react. */
    fun sendOpenSessionBroadcast(context: Context, sessionId: Int) {
        val intent = android.content.Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        context.sendBroadcast(intent)
        Log.d("POC", "Sent OPEN broadcast for session=$sessionId")
    }
}
