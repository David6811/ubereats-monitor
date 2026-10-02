package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Switching a box off is for one shift. The rules file's own timestamp is what
 * the switches are held against, so new rules from the laptop put every box
 * back without the driver having to remember which he turned off.
 */
class NoGoOffTest {

    private val tower = NoGoBox("Tower block", south = -38.0, west = 145.1, north = -37.9, east = 145.2)
    private val mall = NoGoBox("Shopping centre", south = -38.1, west = 145.0, north = -38.0, east = 145.1)

    @Test
    fun `given a box switched off, when the boxes are judged, then it is not among them`() {
        // arrange
        val off = setOf("Tower block")

        // act
        val judged = NoGoOff.apply(listOf(tower, mall), off)

        // assert
        assertEquals(listOf(mall), judged)
    }

    @Test
    fun `given the rules file has changed, when the switches are read, then none are in force`() {
        // arrange  switched off against the file as it stood at 1000
        val switched = BoxesOff(stamp = 1_000, labels = setOf("Tower block"))

        // act  the laptop pushed new rules, so the file now stands at 2000
        val inForce = NoGoOff.inForce(switched, stamp = 2_000)

        // assert
        assertEquals(emptySet<String>(), inForce)
    }

    @Test
    fun `given the same rules file, when the switches are read, then they still hold`() {
        // arrange
        val switched = BoxesOff(stamp = 1_000, labels = setOf("Tower block"))

        // act
        val inForce = NoGoOff.inForce(switched, stamp = 1_000)

        // assert
        assertEquals(setOf("Tower block"), inForce)
    }

    @Test
    fun `given a box switched off, when it is tapped again, then it is back on`() {
        // arrange
        val off = NoGoOff.toggle(emptySet(), "Tower block")

        // affirm
        assertEquals(setOf("Tower block"), off)

        // act
        val back = NoGoOff.toggle(off, "Tower block")

        // assert
        assertEquals(emptySet<String>(), back)
    }

    @Test
    fun `given a label typed in another case, when it is matched, then it is the same box`() {
        // arrange  the label is whatever the driver typed on the laptop
        val off = setOf("tower BLOCK")

        // act
        val judged = NoGoOff.apply(listOf(tower, mall), off)

        // assert
        assertEquals(listOf(mall), judged)
    }
}
