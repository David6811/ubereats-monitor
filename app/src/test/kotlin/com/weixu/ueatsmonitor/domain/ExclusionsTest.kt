package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExclusionsTest {

    private val excluded = Excluded(set = "晚上", stamp = 1_000, suburbs = setOf("Dandenong"))

    @Test
    fun `given exclusions made against the live set, when asked what applies, then they do`() {
        // arrange
        val live = "晚上"

        // act
        val inForce = Exclusions.inForce(excluded, live, stamp = 1_000)

        // assert
        assertEquals(setOf("Dandenong"), inForce)
    }

    @Test
    fun `given the set has been switched since, when asked what applies, then nothing does`() {
        // arrange
        val live = "默认"

        // act
        val inForce = Exclusions.inForce(excluded, live, stamp = 1_000)

        // assert
        assertEquals(emptySet<String>(), inForce)
    }

    @Test
    fun `given new rules have been pushed since, when asked what applies, then nothing does`() {
        // arrange
        val pushedAgain = 2_000L

        // act
        val inForce = Exclusions.inForce(excluded, "晚上", pushedAgain)

        // assert
        assertEquals(emptySet<String>(), inForce)
    }

    @Test
    fun `given a suburb is excluded, when the set is filtered, then it is gone`() {
        // arrange
        val suburbs = setOf("Noble Park", "Dandenong", "Springvale")

        // act
        val left = Exclusions.apply(suburbs, setOf("dandenong"))

        // assert
        assertEquals(setOf("Noble Park", "Springvale"), left)
    }

    @Test
    fun `given a suburb already excluded, when it is toggled, then it comes back`() {
        // arrange
        val off = setOf("Dandenong", "Springvale")

        // act
        val next = Exclusions.toggle(off, "Dandenong")

        // assert
        assertEquals(setOf("Springvale"), next)
    }
}
