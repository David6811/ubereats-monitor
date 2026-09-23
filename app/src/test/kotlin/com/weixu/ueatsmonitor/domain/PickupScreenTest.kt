package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickupScreenTest {

    /** Read off the phone by the accessibility tree, exactly as it came. */
    private val screen = listOf(
        "Wells Road",
        "Home",
        "0 | 15",
        "Search for places",
        "Unable to go offline",
        "Preferences",
        "12 min",
        "9 km",
        "Trip planner",
        "Agenda",
        "Merchant logo",
        "Guzman y Gomez",
        "278 Centre Dandenong Rd, Dingley Village VIC 3172, Australia",
        "Merchant Note:  Dedicated GYG car park directly outside the standalone " +
            "drive-thru Restaurant. Bring thermal bag and check in with store staff.",
        "Pick up 1 order",
        "Help and support",
        "Complete pickup",
        "Safety Toolkit",
    )

    /**
     * The same shop as Uber renders it elsewhere: suburb and postcode, no state.
     * Read off the phone at 11:55 on 12 Sept.
     */
    private val withoutState = listOf(
        "Home",
        "${'$'}10.38",
        "Search for places",
        "Safety Toolkit",
        "Navigate",
        "Coles - Keysborough",
        "317 Cheltenham Rd, Keysborough 3173",
        "Unable to go offline",
        "Preferences",
        "Pick up 1 order",
        "Complete pickup",
    )

    /** A pharmacy's pickup screen, read off the phone at 18:57 on 13 Sept. */
    private val apac = listOf(
        "Home",
        "1 | 3",
        "Search for places",
        "Unable to go offline",
        "Preferences",
        "1 min",
        "0.2 km",
        "(Chemist2U) Pharmacy 4 Less Parkmore",
        "Trip planner",
        "Agenda",
        "Merchant logo",
        "Shop J01, 317-321 Cheltenham Road, Keysborough South, APAC 3173",
        "Merchant Note:  Pharmacy 4 Less Parkmore. Located in Parkmore Shopping Centre on " +
            "Cheltenham Road, Corner of Cheltenham rd and Kingsclere Ave. Parking available in carpark.",
        "Pick up 1 order",
        "Help and support",
        "Complete pickup",
    )

    private val waiting = listOf("Home", "You're online", "Earnings", "Trip planner")

    @Test
    fun `given the pickup screen, when it is read, then the shop's full address comes back`() {
        // arrange
        val lines = screen

        // act
        val pickup = PickupScreen.read(lines)

        // assert
        assertEquals(
            "278 Centre Dandenong Rd, Dingley Village VIC 3172, Australia",
            pickup?.address,
        )
    }

    @Test
    fun `given the pickup screen, when it is read, then the shop is named, not its logo`() {
        // arrange
        val lines = screen

        // act
        val pickup = PickupScreen.read(lines)

        // assert
        assertEquals("Guzman y Gomez", pickup?.store)
    }

    @Test
    fun `given a shop that wrote a note, when the screen is read, then the note comes back`() {
        // arrange
        val lines = screen

        // act
        val pickup = PickupScreen.read(lines)

        // assert
        assertEquals(
            "Dedicated GYG car park directly outside the standalone drive-thru " +
                "Restaurant. Bring thermal bag and check in with store staff.",
            pickup?.note,
        )
    }

    @Test
    fun `given a shop that wrote no note, when the screen is read, then there is none`() {
        // arrange
        val lines = screen.filterNot { it.startsWith("Merchant Note:") }

        // act
        val pickup = PickupScreen.read(lines)

        // affirm
        assertEquals("Guzman y Gomez", pickup?.store)

        // assert
        assertNull(pickup?.note)
    }

    @Test
    fun `given the waiting-for-offers screen, when it is read, then it is not a pickup`() {
        // arrange
        val lines = waiting

        // act
        val pickup = PickupScreen.read(lines)

        // assert
        assertNull(pickup)
    }

    @Test
    fun `given an address with no state, when the screen is read, then it is still the address`() {
        // arrange
        val lines = withoutState

        // act
        val pickup = PickupScreen.read(lines)

        // affirm  the shop above it is named too
        assertEquals("Coles - Keysborough", pickup?.store)

        // assert
        assertEquals("317 Cheltenham Rd, Keysborough 3173", pickup?.address)
    }

    @Test
    fun `given an address written with APAC, when the screen is read, then it is still the address`() {
        // arrange
        val lines = apac

        // act
        val pickup = PickupScreen.read(lines)

        // assert
        assertEquals("Shop J01, 317-321 Cheltenham Road, Keysborough South, APAC 3173", pickup?.address)
    }

    @Test
    fun `given Uber's labels between the shop and its address, when the screen is read, then the shop is named`() {
        // arrange
        val lines = apac

        // act
        val pickup = PickupScreen.read(lines)

        // assert
        assertEquals("(Chemist2U) Pharmacy 4 Less Parkmore", pickup?.store)
    }

    @Test
    fun `given two orders at one shop, when the pickup screen is read, then it says two`() {
        // arrange  23 Sept 18:46, Nene Chicken: two orders, one offer card
        val lines = listOf(
            "Nene Chicken (Parkmore)",
            "Nene Chicken",
            "L01 Z12/317 Cheltenham Road, Keysborough VIC 3173, APAC, AU",
            "Merchant Note: Please use google map instead of uber map to find store.",
            "Pick up 2 orders",
            "Rebecca Q.",
            "jojey L.",
            "Complete pickup",
        )

        // act
        val pickup = PickupScreen.read(lines)

        // assert
        assertEquals(2, pickup?.orders)
    }

    @Test
    fun `given one order, when the pickup screen is read, then it says one`() {
        // arrange
        val lines = listOf(
            "KFC (Dandenong)",
            "55 Princes Highway, Dandenong VIC 3175, Australia",
            "Pick up 1 order",
            "Complete pickup",
        )

        // act
        val pickup = PickupScreen.read(lines)

        // assert
        assertEquals(1, pickup?.orders)
    }
}
