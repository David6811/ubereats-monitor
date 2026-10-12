package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The suburb a dropoff line is about. Two halves of the same decision read it -
 * the allow list, and the anchor the road lookup searches around - and they
 * have to agree or the second looks for a road in the wrong half of the city.
 */
class DropSuburbTest {

    private val suburbs = listOf(
        Suburb("Blackwood", GeoPoint(-37.4631, 144.3089)),
        Suburb("Carnegie", GeoPoint(-37.8871, 145.0576)),
        Suburb("Flinders", GeoPoint(-38.4759, 145.0243)),
        Suburb("Mentone", GeoPoint(-37.9814, 145.0690)),
    )

    @Test
    fun `given a street named after another suburb, when read, then the end of the line wins`() {
        // arrange  Blackwood is a suburb 88km away and a street in Carnegie
        val dropoff = "Blackwood Street & Chestnut Street, Carnegie"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert
        assertEquals("Carnegie", there?.name)
    }

    @Test
    fun `given a second street named after another suburb, when read, then the end still wins`() {
        // arrange
        val dropoff = "Clyve Avenue & Flinders Street, Mentone"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert
        assertEquals("Mentone", there?.name)
    }

    @Test
    fun `given a line whose end names no suburb, when read, then the rest of it is searched`() {
        // arrange
        val dropoff = "Mentone, somewhere OCR could not read"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert
        assertEquals("Mentone", there?.name)
    }
}
