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
}
