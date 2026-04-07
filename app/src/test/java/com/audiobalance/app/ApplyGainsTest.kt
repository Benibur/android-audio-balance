package com.audiobalance.app

import com.audiobalance.app.util.BalanceMapper
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test

// Standalone helper replicating the composition formula (no Android deps)
fun computeGains(balance: Float, gainOffsetDb: Float): Pair<Float, Float> {
    val (balanceLeft, balanceRight) = BalanceMapper.toGainDb(balance.roundToInt())
    return Pair(balanceLeft + gainOffsetDb, balanceRight + gainOffsetDb)
}

class ApplyGainsTest {

    @Test
    fun `zero balance and zero gainOffset produces zero gains`() {
        val (left, right) = computeGains(0f, 0f)
        assertEquals(0f, left, 0.01f)
        assertEquals(0f, right, 0.01f)
    }

    @Test
    fun `balance right 50 with gainOffset -6 attenuates left more`() {
        // balance=50 -> right positive -> left attenuated: leftDb=-60*0.5=-30, rightDb=0
        // with gainOffset=-6: left=-30+(-6)=-36, right=0+(-6)=-6
        val (left, right) = computeGains(50f, -6f)
        assertEquals(-36f, left, 0.01f)
        assertEquals(-6f, right, 0.01f)
    }

    @Test
    fun `balance left -50 with gainOffset -6 attenuates right more`() {
        // balance=-50 -> left positive -> rightDb=-60*0.5=-30, leftDb=0
        // with gainOffset=-6: left=0+(-6)=-6, right=-30+(-6)=-36
        val (left, right) = computeGains(-50f, -6f)
        assertEquals(-6f, left, 0.01f)
        assertEquals(-36f, right, 0.01f)
    }

    @Test
    fun `full right balance 100 with gainOffset -12`() {
        // balance=100 -> leftDb=-60, rightDb=0
        // with gainOffset=-12: left=-60+(-12)=-72, right=0+(-12)=-12
        val (left, right) = computeGains(100f, -12f)
        assertEquals(-72f, left, 0.01f)
        assertEquals(-12f, right, 0.01f)
    }

    @Test
    fun `center balance with gainOffset -12 applies equally to both channels`() {
        // balance=0 -> leftDb=0, rightDb=0
        // with gainOffset=-12: left=-12, right=-12
        val (left, right) = computeGains(0f, -12f)
        assertEquals(-12f, left, 0.01f)
        assertEquals(-12f, right, 0.01f)
    }
}
