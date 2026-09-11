package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureKeepTest {

    /** Newest first, as the directory is read. */
    private val names = listOf("f5", "card4", "f3", "card2", "f1")
    private val offers = setOf("card4", "card2")

    @Test
    fun `given more ordinary frames than are kept, when pruning, then the oldest of them go`() {
        // arrange
        val keepFrames = 2

        // act
        val doomed = CaptureKeep.toDelete(names, offers, keepFrames, keepOffers = 10)

        // assert  f5 and f3 are the two newest ordinary frames; f1 is the third
        assertEquals(setOf("f1"), doomed)
    }

    @Test
    fun `given offer frames older than the ordinary ones kept, when pruning, then they stay`() {
        // arrange  nothing ordinary survives at all
        val keepFrames = 0

        // act
        val doomed = CaptureKeep.toDelete(names, offers, keepFrames, keepOffers = 10)

        // affirm
        assertEquals(false, doomed.contains("card2"))

        // assert
        assertEquals(setOf("f5", "f3", "f1"), doomed)
    }

    @Test
    fun `given more offer frames than are kept, when pruning, then the oldest offers go too`() {
        // arrange
        val keepOffers = 1

        // act
        val doomed = CaptureKeep.toDelete(names, offers, keepFrames = 10, keepOffers = keepOffers)

        // assert
        assertEquals(setOf("card2"), doomed)
    }

    @Test
    fun `given fewer frames than the caps, when pruning, then nothing goes`() {
        // arrange
        val plenty = 100

        // act
        val doomed = CaptureKeep.toDelete(names, offers, plenty, plenty)

        // assert
        assertEquals(emptySet<String>(), doomed)
    }
}
