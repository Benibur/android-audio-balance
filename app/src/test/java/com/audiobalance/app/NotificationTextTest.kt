package com.audiobalance.app

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test

// Standalone helper replicating notification formatting logic (no Android deps)
fun formatNotificationText(deviceName: String?, balance: Int, gainOffsetDb: Float = 0f): String {
    val name = deviceName?.takeIf { it.isNotBlank() } ?: "BT Device"
    val balanceText = when {
        balance > 0  -> "R+${balance}%"
        balance < 0  -> "L+${-balance}%"
        else         -> "Center"
    }
    val gainText = if (gainOffsetDb != 0f) " \u2022 Vol: ${gainOffsetDb.roundToInt()} dB" else ""
    return "$name \u2022 Balance: $balanceText$gainText"
}

class NotificationTextTest {

    @Test
    fun `device with balance right 50 and no gain offset shows no vol`() {
        val result = formatNotificationText("Bose QC35", 50, 0f)
        assertEquals("Bose QC35 \u2022 Balance: R+50%", result)
    }

    @Test
    fun `device with center balance and gainOffset -6 shows vol`() {
        val result = formatNotificationText("Bose QC35", 0, -6f)
        assertEquals("Bose QC35 \u2022 Balance: Center \u2022 Vol: -6 dB", result)
    }

    @Test
    fun `device with balance left -30 and gainOffset -3 shows both`() {
        val result = formatNotificationText("Bose QC35", -30, -3f)
        assertEquals("Bose QC35 \u2022 Balance: L+30% \u2022 Vol: -3 dB", result)
    }

    @Test
    fun `null device name shows BT Device`() {
        val result = formatNotificationText(null, 0, 0f)
        assertEquals("BT Device \u2022 Balance: Center", result)
    }

    @Test
    fun `device with center balance and zero gain shows no vol`() {
        val result = formatNotificationText("Bose QC35", 0, 0f)
        assertEquals("Bose QC35 \u2022 Balance: Center", result)
    }
}
