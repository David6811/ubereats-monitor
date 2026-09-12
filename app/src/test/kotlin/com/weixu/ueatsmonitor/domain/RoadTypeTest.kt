package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoadTypeTest {

    @Test
    fun `given a destination on a lane, when the road is read, then it is refused`() {
        // arrange  read off the card on 11 Sept; the door was a flat above a shop
        val dropoff = "Balcombe Road & Granary Lane, Mentone"

        // act
        val road = RoadType.refusedIn(dropoff)

        // assert
        assertEquals("Lane", road)
    }

    @Test
    fun `given the abbreviated form, when the road is read, then it is refused too`() {
        // arrange
        val dropoff = "Wells Ln, Chelsea Heights"

        // act
        val road = RoadType.refusedIn(dropoff)

        // assert
        assertEquals("Ln", road)
    }

    @Test
    fun `given a destination on a highway, when the road is read, then it is refused`() {
        // arrange
        val dropoff = "Bank Road & Nepean Highway, Edithvale"

        // act
        val road = RoadType.refusedIn(dropoff)

        // assert
        assertEquals("Highway", road)
    }

    @Test
    fun `given an ordinary street and court, when the road is read, then nothing refuses it`() {
        // arrange
        val dropoff = "Ashleigh Street & Jean Court, Keysborough"

        // act
        val road = RoadType.refusedIn(dropoff)

        // assert
        assertNull(road)
    }

    @Test
    fun `given a street whose name merely contains the letters, when the road is read, then it is not refused`() {
        // arrange  Kiln ends in ln and Lanark begins with lan; neither is a lane
        val dropoff = "Kiln Road & Lanark Way, Braeside"

        // act
        val road = RoadType.refusedIn(dropoff)

        // assert
        assertNull(road)
    }
}
