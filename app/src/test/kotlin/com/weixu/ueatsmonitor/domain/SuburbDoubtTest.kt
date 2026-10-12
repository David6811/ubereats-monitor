package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SuburbDoubtTest {

    private val braeside = GeoPoint(-37.9930, 145.1180)
    private val melbourne = Suburb("Melbourne", GeoPoint(-37.8136, 144.9631))
    private val mentone = Suburb("Mentone", GeoPoint(-37.9814, 145.0690))

    @Test
    fun `given a short trip and a suburb across the city, when weighed, then the name is doubted`() {
        // arrange  the card says 3 miles; Melbourne is 15 miles from Braeside
        val trip = Miles(3.0)

        // act
        val doubted = SuburbDoubt.tooFarToBe(melbourne, braeside, trip)

        // assert
        assertEquals(true, doubted)
    }

    @Test
    fun `given a trip as long as the suburb is far, when weighed, then the name stands`() {
        // arrange  a real run into the city: the card says 15 miles and Melbourne is 15 away
        val trip = Miles(15.0)

        // act
        val doubted = SuburbDoubt.tooFarToBe(melbourne, braeside, trip)

        // assert
        assertEquals(false, doubted)
    }

    @Test
    fun `given a suburb next door, when weighed, then the name stands`() {
        // arrange
        val trip = Miles(2.0)

        // act
        val doubted = SuburbDoubt.tooFarToBe(mentone, braeside, trip)

        // assert
        assertEquals(false, doubted)
    }

    @Test
    fun `given no fix for the car, when weighed, then nothing is doubted`() {
        // arrange
        val trip = Miles(3.0)

        // act
        val doubted = SuburbDoubt.tooFarToBe(melbourne, null, trip)

        // assert
        assertEquals(false, doubted)
    }

    @Test
    fun `given a card with no distance on it, when weighed, then nothing is doubted`() {
        // arrange
        val trip = null

        // act
        val doubted = SuburbDoubt.tooFarToBe(melbourne, braeside, trip)

        // assert
        assertEquals(false, doubted)
    }
}
