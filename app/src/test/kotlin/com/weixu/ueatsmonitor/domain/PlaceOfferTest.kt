package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceOfferTest {

    private val home = Place("Home", GeoPoint(-38.0, 145.1), Place.Kind.SAVED)
    private val depot = Place("Dandenong depot", GeoPoint(-37.98, 145.21), Place.Kind.SAVED)
    private val suburb = Place("Dandenong", GeoPoint(-37.98, 145.21), Place.Kind.SUBURB)
    private val shop = Place("Home Thai", GeoPoint(-37.9, 145.0), Place.Kind.SHOP)

    @Test
    fun `given nothing typed, when places are offered, then every saved place is offered`() {
        // arrange
        val typed = ""

        // act
        val offered = PlaceSearch.offered(typed, saved = listOf(home, depot), known = listOf(suburb, shop))

        // assert
        assertEquals(listOf(home, depot), offered)
    }

    @Test
    fun `given a word that matches a saved place and a suburb, when places are offered, then the saved one comes first`() {
        // arrange
        val typed = "dand"

        // act
        val offered = PlaceSearch.offered(typed, saved = listOf(home, depot), known = listOf(suburb, shop))

        // assert
        assertEquals(listOf(depot, suburb), offered)
    }

    @Test
    fun `given a saved place and a shop of the same name, when places are offered, then the name appears once`() {
        // arrange
        val savedThai = Place("Home Thai", GeoPoint(-37.9, 145.0), Place.Kind.SAVED)

        // act
        val offered = PlaceSearch.offered("home thai", saved = listOf(savedThai), known = listOf(shop))

        // assert
        assertEquals(listOf(savedThai), offered)
    }
}
