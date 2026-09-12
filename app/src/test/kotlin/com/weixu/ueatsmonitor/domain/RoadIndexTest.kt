package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RoadIndexTest {

    private val keysborough = Suburb("Keysborough", GeoPoint(-38.0054, 145.1674))

    /** The corner the card named on 12 Sept, and one far away that shares a name. */
    private val crossings = listOf(
        Crossing("Ashleigh Street", "Jean Court", GeoPoint(-37.9934, 145.1808)),
        Crossing("Ashleigh Street", "Jean Court", GeoPoint(-37.8200, 145.1200)),
    )

    private val roads = listOf(
        Road("Wells Road", listOf(GeoPoint(-38.0231, 145.1218), GeoPoint(-38.0102, 145.1330))),
        Road("Ashleigh Street", listOf(GeoPoint(-37.9938, 145.1795))),
    )

    @Test
    fun `given both streets of a corner, when the card is placed, then it is that corner`() {
        // arrange
        val dropoff = "Ashleigh Street & Jean Court, Keysborough"

        // act
        val spot = RoadIndex.find(dropoff, keysborough, crossings, roads)

        // assert
        assertEquals(Spot.AtCrossing(GeoPoint(-37.9934, 145.1808), "Ashleigh Street", "Jean Court"), spot)
    }

    @Test
    fun `given the same corner with OCR's crumbs on it, when the card is placed, then it is still that corner`() {
        // arrange  as it came off the phone, twice, on 12 Sept
        val dropoff = "a Ashleigh Street & Jean Court, sb Keysborough J./KTT|T3.T0"

        // act
        val spot = RoadIndex.find(dropoff, keysborough, crossings, roads)

        // assert
        assertEquals(GeoPoint(-37.9934, 145.1808), spot.at)
    }

    @Test
    fun `given a corner of the same name in another part of town, when the card is placed, then the near one wins`() {
        // arrange  the far crossing is first in the list, so only the suburb can rule it out
        val dropoff = "Ashleigh Street & Jean Court, Keysborough"
        val farFirst = crossings.reversed()

        // act
        val spot = RoadIndex.find(dropoff, keysborough, farFirst, roads)

        // assert
        assertEquals(GeoPoint(-37.9934, 145.1808), spot.at)
    }

    @Test
    fun `given a card naming one street only, when it is placed, then it is on that road`() {
        // arrange
        val dropoff = "Wells Road, Chelsea Heights"
        val chelseaHeights = Suburb("Chelsea Heights", GeoPoint(-38.0231, 145.1225))

        // act
        val spot = RoadIndex.find(dropoff, chelseaHeights, crossings, roads)

        // assert
        assertEquals(Spot.OnRoad(GeoPoint(-38.0231, 145.1218), "Wells Road"), spot)
    }

    @Test
    fun `given a destination no street matches, when it is placed, then the suburb is all there is`() {
        // arrange  the pickup was read into the dropoff; there is no street in it
        val dropoff = "Nandos (Braecide)"

        // act
        val spot = RoadIndex.find(dropoff, keysborough, crossings, roads)

        // assert
        assertEquals(Spot.InSuburb(GeoPoint(-38.0054, 145.1674), "Keysborough"), spot)
    }

    @Test
    fun `given two streets the map gives no junction, when the card is placed, then it is between them`() {
        // arrange  a freeway carries Racecourse Drive over Sandown Road
        val springvale = Suburb("Springvale", GeoPoint(-37.9483, 145.1518))
        val apart = listOf(
            Road("Racecourse Drive", listOf(GeoPoint(-37.9500, 145.1600))),
            Road("Sandown Road", listOf(GeoPoint(-37.9520, 145.1600))),
        )

        // act
        val spot = RoadIndex.find("Racecourse Drive & Sandown Road, Springvale", springvale, emptyList(), apart)

        // assert  halfway between their nearest points
        assertEquals(
            Spot.NearCrossing(GeoPoint(-37.9510, 145.1600), "Racecourse Drive", "Sandown Road"),
            spot,
        )
    }

    @Test
    fun `given the card abbreviating the road type wrongly, when it is placed, then the stem still finds it`() {
        // arrange  the card said Wells Ln; the street he delivered to is Wells Rd
        val chelseaHeights = Suburb("Chelsea Heights", GeoPoint(-38.0231, 145.1225))

        // act
        val spot = RoadIndex.find("Wells Ln, Chelsea Heights", chelseaHeights, crossings, roads)

        // assert
        assertEquals(Spot.OnRoad(GeoPoint(-38.0231, 145.1218), "Wells Road"), spot)
    }

    @Test
    fun `given a road whose stem sits inside another's, when the card is placed, then the longer name is not taken`() {
        // arrange  "Watt" inside "Watton" once put Watt Street where Watton Close was
        val claytonSouth = Suburb("Clayton South", GeoPoint(-37.9415, 145.1245))
        val both = listOf(
            Road("Watt Street", listOf(GeoPoint(-37.9400, 145.1200))),
            Road("Watton Close", listOf(GeoPoint(-37.9420, 145.1260))),
        )

        // act
        val spot = RoadIndex.find("Watton Close, Clayton South", claytonSouth, emptyList(), both)

        // assert
        assertEquals(Spot.OnRoad(GeoPoint(-37.9420, 145.1260), "Watton Close"), spot)
    }
}
