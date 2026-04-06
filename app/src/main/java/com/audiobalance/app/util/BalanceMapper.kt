package com.audiobalance.app.util

object BalanceMapper {
    /**
     * Convert user-facing balance (-100 full left .. 0 center .. +100 full right)
     * to (leftGainDb, rightGainDb) pair for DynamicsProcessing.setInputGainbyChannel().
     *
     * -60 dB attenuation on the attenuated channel — confirmed adequate in Phase 1 POC.
     */
    fun toGainDb(balance: Int): Pair<Float, Float> {
        val fraction = balance.coerceIn(-100, 100) / 100f
        val leftDb  = if (fraction > 0) -60f * fraction else 0f
        val rightDb = if (fraction < 0) -60f * (-fraction) else 0f
        return Pair(leftDb, rightDb)
    }
}
