package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every line here was read off Uber's own ongoing notification during the
 * shift of 4 Oct, in the order it arrived.
 */
class TripNoticeTest {

    @Test
    fun `given the line between jobs, when it is read, then nothing is running`() {
        // arrange
        val line = "You are currently online."

        // act
        val state = TripNotice.read(line)

        // assert
        assertEquals(TripState.Idle, state)
    }

    @Test
    fun `given going to a shop, when it is read, then the driver is on the way to it`() {
        // arrange
        val line = "Going to Captain Makos Fish and Chips"

        // act
        val state = TripNotice.read(line)

        // assert
        assertEquals(TripState.ToShop("Captain Makos Fish and Chips"), state)
    }

    @Test
    fun `given arrived at a shop, when it is read, then the driver is there`() {
        // arrange
        val line = "Arrived at Sushi Sushi (Waverley Gardens)"

        // act
        val state = TripNotice.read(line)

        // assert
        assertEquals(TripState.AtShop("Sushi Sushi (Waverley Gardens)"), state)
    }

    @Test
    fun `given going to a street address, when it is read, then the driver is delivering`() {
        // arrange
        val line = "Going to 27 Buldah Street, Dandenong North VIC 3175, Australia"

        // act
        val state = TripNotice.read(line)

        // assert
        assertEquals(TripState.ToCustomer("27 Buldah Street, Dandenong North VIC 3175, Australia"), state)
    }

    @Test
    fun `given a unit number in the address, when it is read, then the driver is still delivering`() {
        // arrange
        val line = "Going to 2/53 Ellendale Rd, Noble Park VIC 3174, Australia"

        // act
        val state = TripNotice.read(line)

        // assert
        assertEquals(TripState.ToCustomer("2/53 Ellendale Rd, Noble Park VIC 3174, Australia"), state)
    }

    @Test
    fun `given the line at the door, when it is read, then the driver is at the customer`() {
        // arrange
        val line = "Leave the order at Callum's door"

        // act
        val state = TripNotice.read(line)

        // assert
        assertEquals(TripState.AtCustomer, state)
    }

    @Test
    fun `given the other line at the door, when it is read, then the driver is at the customer`() {
        // arrange
        val line = "Meet at door for Michael's order"

        // act
        val state = TripNotice.read(line)

        // assert
        assertEquals(TripState.AtCustomer, state)
    }

    @Test
    fun `given a shop whose name starts with a number, when it is read, then it is not an address`() {
        // arrange  a shop is named, not numbered: no street follows the digits
        val line = "Going to 7-Eleven (Dandenong North)"

        // act
        val state = TripNotice.read(line)

        // assert
        assertEquals(TripState.ToShop("7-Eleven (Dandenong North)"), state)
    }

    @Test
    fun `given a line Uber did not write, when it is read, then nothing is claimed`() {
        // arrange
        val line = "Your weekly summary is ready"

        // act
        val state = TripNotice.read(line)

        // assert
        assertEquals(null, state)
    }
}
