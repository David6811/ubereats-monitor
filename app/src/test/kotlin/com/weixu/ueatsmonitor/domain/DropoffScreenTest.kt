package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DropoffScreenTest {

    /** Read off the phone by the accessibility tree, exactly as it came. */
    private val screen = listOf(
        "Home",
        "${'$'}0.00",
        "Search for places",
        "9 min",
        "4.5 km",
        "Trip planner",
        "Agenda",
        "Lewis A.",
        "12/98 Collins St",
        "Mentone VIC",
        "Apt / Unit / Floor:",
        "Unit 12",
        "Note from customer Buzz 12 on the intercom. Please come my door - I am " +
            "on crutches so limited mobility. Thank you so much",
        "Drop off 1 order",
        "Help and support",
        "Complete delivery",
        "Head north",
    )

    @Test
    fun `given the delivery screen, when it is read, then the street and suburb come back together`() {
        // arrange
        val lines = screen

        // act
        val dropoff = DropoffScreen.read(lines)

        // assert
        assertEquals("12/98 Collins St, Mentone VIC", dropoff?.address)
    }

    @Test
    fun `given a unit on the delivery screen, when it is read, then the unit comes back`() {
        // arrange
        val lines = screen

        // act
        val dropoff = DropoffScreen.read(lines)

        // assert
        assertEquals("Unit 12", dropoff?.unit)
    }

    @Test
    fun `given a customer who left a note, when the screen is read, then the note comes back whole`() {
        // arrange
        val lines = screen

        // act
        val dropoff = DropoffScreen.read(lines)

        // assert
        assertEquals(
            "Buzz 12 on the intercom. Please come my door - I am on crutches so " +
                "limited mobility. Thank you so much",
            dropoff?.note,
        )
    }

    @Test
    fun `given a delivery with no unit and no note, when the screen is read, then the address still comes back`() {
        // arrange
        val lines = screen.filterNot {
            it == "Apt / Unit / Floor:" || it == "Unit 12" || it.startsWith("Note from customer")
        }

        // act
        val dropoff = DropoffScreen.read(lines)

        // affirm
        assertNull(dropoff?.unit)

        // assert
        assertEquals("12/98 Collins St, Mentone VIC", dropoff?.address)
    }

    @Test
    fun `given a delivery screen, when its suburb is asked for, then the state is not part of it`() {
        // arrange
        val dropoff = DropoffScreen.read(screen)!!

        // act
        val suburb = DropoffScreen.suburbOf(dropoff)

        // assert
        assertEquals("Mentone", suburb)
    }

    @Test
    fun `given the pickup screen, when it is read as a delivery, then nothing comes back`() {
        // arrange
        val lines = listOf(
            "Guzman y Gomez",
            "278 Centre Dandenong Rd, Dingley Village VIC 3172, Australia",
            "Pick up 1 order",
            "Complete pickup",
        )

        // act
        val dropoff = DropoffScreen.read(lines)

        // assert
        assertNull(dropoff)
    }
}
