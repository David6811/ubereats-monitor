package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChipTextTest {

    private val suburbs = listOf(
        Suburb("Springvale", GeoPoint(-37.9483, 145.1518)),
        Suburb("Noble Park", GeoPoint(-37.9670, 145.1740)),
        Suburb("Noble Park North", GeoPoint(-37.9540, 145.1810)),
    )

    private val stores = StoreKinds.parse(
        "Guzman y Gomez,-37.94,145.15,fast_food,MALL,0,underground,12"
    )

    private val card = OfferCard(
        isMatch = false,
        payout = Cents(803),
        duration = Minutes(18),
        distance = Miles(5.0),
        pickup = "9 Guzman y Gomez (Springvale)",
        dropoff = "Aybrook Court & Grovelands Drive, Noble Park North",
        stops = emptyList(),
    )

    @Test
    fun `given a card naming both suburbs, when the route is written, then it is suburb to suburb with the shop's kind`() {
        // arrange
        val offer = card

        // act
        val route = ChipText.route(offer, suburbs, stores)

        // assert
        assertEquals("Springvale（快餐·商场） → Noble Park North", route)
    }

    @Test
    fun `given a card whose dropoff names no suburb, when the route is written, then that end names itself`() {
        // arrange
        val offer = card.copy(dropoff = "Boundary Road & Wells Road, Braeside")

        // act
        val route = ChipText.route(offer, suburbs, stores)

        // assert
        assertEquals("Springvale（快餐·商场） → Boundary Road…", route)
    }

    @Test
    fun `given a pickup OCR padded with its suburb in brackets, when it is shortened, then only the shop name is left`() {
        // arrange
        val pickup = "9 Amritsari Sweets & Snacks (Noble Park)"

        // act
        val name = ChipText.shorten(pickup)

        // assert
        assertEquals("Amritsari Swee…", name)
    }

    @Test
    fun `given a card naming no suburb at all, when the route is written, then both ends name themselves`() {
        // arrange
        val offer = card.copy(pickup = "Some Shop", dropoff = "Somewhere Else")

        // act
        val route = ChipText.route(offer, suburbs, stores)

        // assert
        assertEquals("Some Shop → Somewhere Else", route)
    }

    @Test
    fun `given a card of eight dollars over eight kilometres, when the rate is written, then it is a dollar a kilometre`() {
        // arrange  5.0 miles is 8.047 km, and 8.03 over that is 0.998
        val offer = card

        // act
        val rate = ChipText.rate(offer)

        // assert
        assertEquals("${'$'}1.00/公里", rate)
    }

    @Test
    fun `given a card whose distance was unreadable, when the rate is written, then there is no line`() {
        // arrange
        val offer = card.copy(distance = null)

        // act
        val rate = ChipText.rate(offer)

        // assert
        assertNull(rate)
    }

    @Test
    fun `given a card measured in miles, when the distance is written, then it is said in kilometres`() {
        // arrange  5.0 miles
        val offer = card

        // act
        val distance = ChipText.distance(offer)

        // assert
        assertEquals("8.0 公里", distance)
    }

    @Test
    fun `given a card whose distance was unreadable, when the distance is written, then there is no line`() {
        // arrange
        val offer = card.copy(distance = null)

        // act
        val distance = ChipText.distance(offer)

        // assert
        assertNull(distance)
    }
}
