package com.audiobalance.app.poc

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.P)
class AudioEffectPoc {
    private var dp: DynamicsProcessing? = null
    var lastAttemptLog: String = "no attempt yet"
        private set

    fun createOnSession(sessionId: Int): Boolean {
        releaseEffect()
        return try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false, 0,
                false, 0,
                false, 0,
                false
            ).build()
            val created = DynamicsProcessing(0, sessionId, config)
            val enableResult = created.setEnabled(true)
            val hasControl = created.hasControl()
            dp = created
            lastAttemptLog = "DynamicsProcessing session=$sessionId created OK, setEnabled=$enableResult hasControl=$hasControl"
            Log.d("POC", lastAttemptLog)
            true
        } catch (e: RuntimeException) {
            lastAttemptLog = "DynamicsProcessing session=$sessionId FAILED: ${e.javaClass.simpleName}: ${e.message}"
            Log.e("POC", lastAttemptLog, e)
            false
        }
    }

    /** balanceFraction in [-1.0, +1.0]; -1 = full left, +1 = full right, 0 = center */
    fun applyBalance(balanceFraction: Float) {
        val dp = this.dp ?: run {
            Log.w("POC", "applyBalance called but no effect")
            return
        }
        val leftGainDb  = if (balanceFraction > 0) -60f * balanceFraction else 0f
        val rightGainDb = if (balanceFraction < 0) -60f * (-balanceFraction) else 0f
        try {
            dp.setInputGainbyChannel(0, leftGainDb)
            dp.setInputGainbyChannel(1, rightGainDb)
            Log.d("POC", "Balance applied: L=${leftGainDb}dB R=${rightGainDb}dB")
        } catch (e: RuntimeException) {
            Log.e("POC", "setInputGainbyChannel failed: ${e.message}", e)
        }
    }

    fun releaseEffect() {
        dp?.let {
            try { it.setEnabled(false) } catch (_: RuntimeException) {}
            try { it.release() } catch (_: RuntimeException) {}
        }
        dp = null
    }

    // ================================================================
    // Session 0 GLOBAL test — "create BEFORE media player" protocol
    // ================================================================

    private var globalDp: DynamicsProcessing? = null

    fun createGlobalSession0(): String {
        releaseGlobal()
        return try {
            val config = DynamicsProcessing.Config.Builder(
                0,      // variant: default (VARIANT_FAVOR_FREQUENCY_RESOLUTION=0)
                2,      // channelCount: request 2 explicitly
                true,   // preEqInUse
                0,      // preEqBandCount
                false,  // mbcInUse
                0,      // mbcBandCount
                false,  // postEqInUse
                0,      // postEqBandCount
                true    // limiterInUse
            ).build()
            val dp = DynamicsProcessing(0, 0, config) // priority=0, sessionId=0 LITERAL GLOBAL
            val enabled = dp.setEnabled(true)
            val hasControl = dp.hasControl()
            // Probe channel count: try to read channel 1; if it throws, it's mono.
            val channelCount = probeChannelCount(dp)
            globalDp = dp
            "Global DP session=0 created OK | setEnabled=$enabled | hasControl=$hasControl | channelCount=$channelCount"
        } catch (t: Throwable) {
            "Global DP session=0 FAILED: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    /**
     * Probes the actual channel count by attempting to read input gain on channel 1.
     * DynamicsProcessing has no public getChannelCount() — we infer via try/catch.
     * Returns 2 if stereo confirmed, 1 if only channel 0 is available.
     */
    private fun probeChannelCount(dp: DynamicsProcessing): Int {
        return try {
            // If channel 1 doesn't exist, this throws (IllegalArgumentException or similar)
            dp.setInputGainbyChannel(1, 0f)
            2
        } catch (t: Throwable) {
            1
        }
    }

    fun applyGlobalBalance(leftDb: Float, rightDb: Float): String {
        val dp = globalDp ?: return "Global DP not created yet — tap 'Attach DP session 0 GLOBAL' first"
        return try {
            val channelCount = probeChannelCount(dp)
            if (channelCount < 2) {
                return "Global DP has only $channelCount channel(s) — balance impossible on mono"
            }
            dp.setInputGainbyChannel(0, leftDb)   // channel 0 = left
            dp.setInputGainbyChannel(1, rightDb)  // channel 1 = right
            "Global balance applied: L=${leftDb}dB R=${rightDb}dB (channels=$channelCount)"
        } catch (t: Throwable) {
            "Global balance FAILED: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    fun releaseGlobal(): String {
        return try {
            globalDp?.let {
                try { it.setEnabled(false) } catch (_: Throwable) {}
                it.release()
            }
            globalDp = null
            "Global DP released"
        } catch (t: Throwable) {
            "Global DP release FAILED: ${t.message}"
        }
    }
}
