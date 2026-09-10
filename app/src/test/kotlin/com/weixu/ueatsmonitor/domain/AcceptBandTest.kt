package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every colour here was measured off a real card, not chosen. */
class AcceptBandTest {

    @Test
    fun `given the green Accept button, when tested, then a button is there`() {
        // arrange
        val samples = band(GREEN_FULL, 85)

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertTrue(holds)
    }

    @Test
    fun `given the black Match button, when tested, then a button is there`() {
        // arrange
        val samples = band(BLACK, 75)

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertTrue(holds)
    }

    @Test
    fun `given the countdown half emptied, when tested, then a button is still there`() {
        // arrange
        val samples = IntArray(100) { index -> if (index < 50) GREEN_FULL else GREEN_EMPTY }

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertTrue(holds)
    }

    @Test
    fun `given the countdown almost emptied, when tested, then a button is still there`() {
        // arrange
        val samples = IntArray(100) { index -> if (index < 3) GREEN_FULL else GREEN_EMPTY }

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertTrue(holds)
    }

    @Test
    fun `given the white card body alone, when tested, then no button is there`() {
        // arrange
        val samples = IntArray(100) { WHITE }

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertFalse(holds)
    }

    @Test
    fun `given a scattering of map colours, when tested, then no button is there`() {
        // arrange
        val samples = IntArray(100) { index ->
            when (index % 5) {
                0 -> 0xFFD8ECD8.toInt()
                1 -> 0xFFEFEFEF.toInt()
                2 -> 0xFFC8D8F0.toInt()
                3 -> 0xFFF4E8D0.toInt()
                else -> WHITE
            }
        }

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertFalse(holds)
    }

    private fun band(colour: Int, share: Int) = IntArray(100) { index ->
        if (index < share) colour else WHITE
    }

    private companion object {
        val GREEN_FULL = 0xFF108246.toInt()
        val GREEN_EMPTY = 0xFF3F9B6A.toInt()
        val BLACK = 0xFF000000.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
    }
}
