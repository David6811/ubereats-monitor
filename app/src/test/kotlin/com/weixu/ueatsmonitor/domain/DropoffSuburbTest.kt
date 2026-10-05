package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Half the streets in Melbourne are named after a suburb, and Uber writes a
 * destination as "Cross Street & Cross Street, Suburb". Reading every suburb
 * named anywhere on that line threw away 23 jobs on one board, every one of
 * them going somewhere the driver goes.
 *
 * Each line here is one of those jobs.
 */
class DropoffSuburbTest {

    private val suburbs = listOf(
        "Mulgrave", "Newport", "Noble Park", "Dandenong", "Clayton", "Blackburn",
        "Springvale", "Springvale South", "Heatherton", "Rowville", "Kilcunda",
    ).map { Suburb(it, GeoPoint(-37.9, 145.1)) }

    @Test
    fun `given a street named after another suburb, when the drop is read, then the tail wins`() {
        // arrange  5 Oct 18:41, refused as "Newport is not on your list"
        val dropoff = "Ellis Park Avenue & Newport Drive, Mulgrave"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert
        assertEquals("Mulgrave", there?.name)
    }

    @Test
    fun `given Dandenong Road in Noble Park, when the drop is read, then it is Noble Park`() {
        // arrange  4 Oct 12:45, refused as "Dandenong is not on your list"
        val dropoff = "Dandenong Road & Joan Court, Noble Park"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert
        assertEquals("Noble Park", there?.name)
    }

    @Test
    fun `given a plain address, when the drop is read, then the tail is still the suburb`() {
        // arrange  3 Oct 21:28, refused as "Blackburn is not on your list"
        val dropoff = "Blackburn Rd, Clayton"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert
        assertEquals("Clayton", there?.name)
    }

    @Test
    fun `given a tail with the state after it, when the drop is read, then the suburb is found`() {
        // arrange  the delivery screen writes the city and state on after the suburb
        val dropoff = "13 Belfort Street, Dandenong Melbourne VIC"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert
        assertEquals("Dandenong", there?.name)
    }

    @Test
    fun `given two suburbs in the tail, when the drop is read, then the longer name wins`() {
        // arrange  "Springvale South" contains "Springvale"
        val dropoff = "Heatherton Road & Westall Road, Springvale South"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert
        assertEquals("Springvale South", there?.name)
    }

    @Test
    fun `given no comma at all, when the drop is read, then the whole line is searched`() {
        // arrange  a line with no comma is still a destination
        val dropoff = "Rowville"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert
        assertEquals("Rowville", there?.name)
    }

    @Test
    fun `given a tail naming no suburb, when the drop is read, then the line is searched`() {
        // arrange  the tail is a postcode or a scrap
        val dropoff = "Kilcunda Drive & Langhorne Crescent, 3178"

        // act
        val there = SuburbIndex.ofDropoff(dropoff, suburbs)

        // assert  better the street's namesake than nothing at all
        assertEquals("Kilcunda", there?.name)
    }
}
