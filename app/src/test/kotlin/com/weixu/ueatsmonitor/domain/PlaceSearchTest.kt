package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSearchTest {

    private val aspendale = Place("Aspendale", GeoPoint(-38.0276, 145.1012), Place.Kind.SUBURB)
    private val gardens = Place("Aspendale Gardens", GeoPoint(-38.0231, 145.1241), Place.Kind.SUBURB)
    private val chelsea = Place("Chelsea", GeoPoint(-38.0520, 145.1180), Place.Kind.SUBURB)
    private val wolf = Place("The Famished Wolf", GeoPoint(-38.0730, 145.1310), Place.Kind.SHOP)
    private val all = listOf(gardens, chelsea, wolf, aspendale)

    @Test
    fun `given a name that several places start with, when typed, then the shortest comes first`() {
        // arrange
        val typed = "aspen"

        // act
        val found = PlaceSearch.matching(typed, all)

        // assert
        assertEquals(listOf(aspendale, gardens), found)
    }

    @Test
    fun `given the start of a later word, when typed, then the place carrying that word is found`() {
        // arrange
        val typed = "gard"

        // act
        val found = PlaceSearch.matching(typed, all)

        // assert
        assertEquals(listOf(gardens), found)
    }

    @Test
    fun `given a name that starts with it and one that merely holds it, when typed, then the start wins`() {
        // arrange
        val typed = "chel"
        val inside = Place("Michelham", GeoPoint(-37.9, 145.0), Place.Kind.SUBURB)

        // act
        val found = PlaceSearch.matching(typed, listOf(inside, chelsea))

        // assert
        assertEquals(listOf(chelsea, inside), found)
    }

    @Test
    fun `given a shop name in a different case, when typed, then it is still found`() {
        // arrange
        val typed = "FAMISHED"

        // act
        val found = PlaceSearch.matching(typed, all)

        // assert
        assertEquals(listOf(wolf), found)
    }

    @Test
    fun `given nothing typed yet, when searched, then nothing is offered`() {
        // arrange
        val typed = "   "

        // act
        val found = PlaceSearch.matching(typed, all)

        // assert
        assertEquals(emptyList<Place>(), found)
    }

    @Test
    fun `given more matches than the screen holds, when searched, then only the limit comes back`() {
        // arrange
        val many = (1..20).map { Place("Aspen $it", GeoPoint(-38.0, 145.0), Place.Kind.SUBURB) }

        // act
        val found = PlaceSearch.matching("aspen", many, limit = 8)

        // assert
        assertEquals(8, found.size)
    }
}
