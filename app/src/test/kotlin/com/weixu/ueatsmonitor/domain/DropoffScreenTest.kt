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
    fun `given a delivery screen with the suburb in capitals and no state, when it is read, then the address still comes back`() {
        // arrange  21 Sept 17:35, the second Coles order
        val lines = listOf(
            "Cyril Grove", "Home", "Search for places", "1 min", "0.9 km",
            "Deliver to Lucyna A.", "Trip Planner", "Agenda",
            "Lucyna A.", "52 Jellicoe St", "NOBLE PARK",
            "Customer note: Leave on doorstep",
            "Drop off 1 order", "Help and support", "Complete delivery",
        )

        // act
        val dropoff = DropoffScreen.read(lines)

        // assert
        assertEquals(
            Dropoff(customer = "Lucyna A.", address = "52 Jellicoe St, Noble Park", unit = null, note = "Leave on doorstep"),
            dropoff,
        )
    }

    @Test
    fun `given an address whose suburb line carried no state, when the suburb is asked for, then it is the last part`() {
        // arrange
        val dropoff = Dropoff(customer = null, address = "52 Jellicoe St, Noble Park", unit = null, note = null)

        // act
        val suburb = DropoffScreen.suburbOf(dropoff)

        // assert
        assertEquals("Noble Park", suburb)
    }

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

    /** The same trip as it first opens: suburb, state and postcode split by commas. */
    private val openingScreen = listOf(
        "Uber Driver notification: Going to 3/49 Argyle Avenue, Chelsea, VIC 3196, AUSTRALIA",
        "Home",
        "18 min",
        "11.3 km",
        "Trip planner",
        "Agenda",
        "Darcy E.",
        "3/49 Argyle Ave",
        "Chelsea, VIC, 3196",
        "Customer note: On the front porch",
        "Scan barcodes",
        "Help and support",
        "Complete delivery",
        "Safety Toolkit",
        "What's going on?",
        "Turn left",
    )

    @Test
    fun `given the delivery screen as it first opens, when it is read, then the address comes back with its postcode`() {
        // arrange
        val lines = openingScreen

        // act
        val dropoff = DropoffScreen.read(lines)

        // assert
        assertEquals("3/49 Argyle Ave, Chelsea VIC 3196", dropoff?.address)
    }

    @Test
    fun `given the delivery screen as it first opens, when its suburb is asked for, then the commas are not part of it`() {
        // arrange
        val dropoff = DropoffScreen.read(openingScreen)!!

        // act
        val suburb = DropoffScreen.suburbOf(dropoff)

        // assert
        assertEquals("Chelsea", suburb)
    }

    @Test
    fun `given the map header repeats the address above the card, when it is read, then the customer is taken from the card`() {
        // arrange
        val lines = listOf(
            "Safety Toolkit",
            "What's going on?",
            "3/49 Argyle Ave",
            "Chelsea, VIC, 3196",
            "8 min",
            "6.4 km",
            "Darcy E.",
            "3/49 Argyle Ave",
            "Chelsea VIC",
            "Customer note: On the front porch",
            "Help and support",
            "Complete delivery",
        )

        // act
        val dropoff = DropoffScreen.read(lines)

        // assert
        assertEquals("Darcy E.", dropoff?.customer)
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
