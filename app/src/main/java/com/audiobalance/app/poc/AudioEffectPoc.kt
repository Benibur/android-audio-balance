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
}
