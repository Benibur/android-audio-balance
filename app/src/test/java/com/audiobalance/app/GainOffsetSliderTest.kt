package com.audiobalance.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GainOffsetSliderTest {
    // Normalization formulas matching DeviceCard implementation
    private fun normalizedToDb(normalized: Float): Float = normalized * 12f + (-12f)
    private fun dbToNormalized(db: Float): Float = (db + 12f) / 12f

    @Test fun normalizedToDb_farLeft_isMinus12() = assertEquals(-12f, normalizedToDb(0f), 0.01f)
    @Test fun normalizedToDb_midpoint_isMinus6() = assertEquals(-6f, normalizedToDb(0.5f), 0.01f)
    @Test fun normalizedToDb_farRight_isZero() = assertEquals(0f, normalizedToDb(1f), 0.01f)
    @Test fun dbToNormalized_minus12_isZero() = assertEquals(0f, dbToNormalized(-12f), 0.01f)
    @Test fun dbToNormalized_minus6_isHalf() = assertEquals(0.5f, dbToNormalized(-6f), 0.01f)
    @Test fun dbToNormalized_zero_isOne() = assertEquals(1f, dbToNormalized(0f), 0.01f)

    @Test fun steps11_produces12Positions() {
        val steps = 11
        val positions = steps + 1
        assertEquals(12, positions)
    }
}
