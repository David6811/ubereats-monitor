package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptBandTest {

    @Test
    fun `given the button green measured off a real card, when tested, then it is recognised`() {
        // arrange
        val pixel = 0xFF108246.toInt()

        // act
        val isGreen = AcceptBand.isUberGreen(pixel)

        // assert
        assertTrue(isGreen)
    }

    @Test
    fun `given the lighter green the countdown empties to, when tested, then it is recognised`() {
        // arrange
        val pixel = 0xFF3F9B6A.toInt()

        // act
        val isGreen = AcceptBand.isUberGreen(pixel)

        // assert
        assertTrue(isGreen)
    }

    @Test
    fun `given a band half filled by the countdown, when tested, then a button is still there`() {
        // arrange
        val samples = IntArray(100) { index ->
            if (index < 50) 0xFF108246.toInt() else 0xFF3F9B6A.toInt()
        }

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertTrue(holds)
    }

    @Test
    fun `given a band the countdown has almost emptied, when tested, then a button is still there`() {
        // arrange
        val samples = IntArray(100) { index ->
            if (index < 3) 0xFF108246.toInt() else 0xFF3F9B6A.toInt()
        }

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertTrue(holds)
    }

    @Test
    fun `given the white card body, when tested, then it is not the button`() {
        // arrange
        val pixel = 0xFFFFFFFF.toInt()

        // act
        val isGreen = AcceptBand.isUberGreen(pixel)

        // assert
        assertFalse(isGreen)
    }

    @Test
    fun `given the map's park green, when tested, then it is not the button`() {
        // arrange
        val pixel = 0xFFD8ECD8.toInt()

        // act
        val isGreen = AcceptBand.isUberGreen(pixel)

        // assert
        assertFalse(isGreen)
    }

    @Test
    fun `given a row that is mostly button, when tested, then a button is there`() {
        // arrange
        val samples = IntArray(100) { index ->
            if (index < 85) 0xFF108246.toInt() else 0xFFFFFFFF.toInt()
        }

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertTrue(holds)
    }

    @Test
    fun `given a row with a few green map pixels, when tested, then no button is there`() {
        // arrange
        val samples = IntArray(100) { index ->
            if (index < 5) 0xFF108246.toInt() else 0xFFEFEFEF.toInt()
        }

        // act
        val holds = AcceptBand.holdsButton(samples)

        // assert
        assertFalse(holds)
    }
}
