package com.weixu.ueatsmonitor.action

import com.weixu.ueatsmonitor.domain.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shift of 4 Oct, replayed. The panel showed the shop of one job beside the
 * door of the one before it while Uber's own screen named a third, so each test
 * here is a moment from that evening.
 */
class CurrentStopTest {

    @Test
    fun `given a job delivered, when the next shop is named, then the last door is gone`() {
        // arrange  Bhukkar delivered to 10 Jon Pl, then "Going to Sugar on tap"
        CurrentStop.saw(TripState.ToShop("Bhukkar"))
        CurrentStop.saw(TripState.ToCustomer("10 Jon Pl, Keysborough VIC 3173, Australia"))
        CurrentStop.saw(TripState.AtCustomer)

        // act
        CurrentStop.saw(TripState.ToShop("Sugar on tap"))

        // assert  the door of the job before is not this job's door
        assertNull(CurrentStop.trip.customer)
    }

    @Test
    fun `given a pickup screen from an earlier job, when it is read, then it is not this job's shop`() {
        // arrange  the notice says Sugar on tap; a stale screen names a Clayton address
        CurrentStop.saw(TripState.ToShop("Sugar on tap"))

        // act
        CurrentStop.pickupScreen("Toast Deli", "1924 Dandenong Rd, Clayton, VIC 3168")

        // assert
        assertEquals("Sugar on tap", CurrentStop.trip.shop)
    }

    @Test
    fun `given the pickup screen of this job, when it is read, then its street address is used`() {
        // arrange
        CurrentStop.saw(TripState.ToShop("Sugar on tap"))

        // act
        CurrentStop.pickupScreen("Sugar on tap", "15/2 Kirkham Road West, Keysborough, VIC, AU, 3173")

        // assert
        assertEquals("15/2 Kirkham Road West, Keysborough, VIC, AU, 3173", CurrentStop.trip.shop)
    }

    @Test
    fun `given the shop named in short, when the screen names it in full, then it is the same shop`() {
        // arrange  the notice says "KFC (Noble Park)", the screen says "KFC"
        CurrentStop.saw(TripState.ToShop("KFC (Noble Park)"))

        // act
        CurrentStop.pickupScreen("KFC", "1 Buckley Street, Noble Park VIC 3174")

        // assert
        assertEquals("1 Buckley Street, Noble Park VIC 3174", CurrentStop.trip.shop)
    }

    @Test
    fun `given the driver goes online, when nothing is running, then there is no stop at all`() {
        // arrange
        CurrentStop.saw(TripState.ToShop("Noble Kebaba"))
        CurrentStop.saw(TripState.ToCustomer("2 Marshall Street, Noble Park VIC 3174, Australia"))

        // act
        CurrentStop.saw(TripState.Idle)

        // assert
        assertEquals(true, CurrentStop.trip.empty)
    }

    @Test
    fun `given the notice named the door, when a delivery screen names another, then the notice stands`() {
        // arrange
        CurrentStop.saw(TripState.ToShop("YOMG (Mordialloc)"))
        CurrentStop.saw(TripState.ToCustomer("170 Nepean Hwy Unit 3, Aspendale, VIC 3195, AU"))

        // act  a delivery screen for the other half of the batch
        CurrentStop.dropoffScreen("66 Hughes Avenue, Chelsea Melbourne VIC")

        // assert
        assertEquals("170 Nepean Hwy Unit 3, Aspendale, VIC 3195, AU", CurrentStop.trip.customer)
    }

    @Test
    fun `given the second shop of a batch, when it is arrived at, then it becomes the job in hand`() {
        // arrange  20:45 arrived at YOMG, 20:48 arrived at Woolworths, no "Going to" between
        CurrentStop.saw(TripState.ToShop("YOMG (Mordialloc)"))

        // act
        CurrentStop.saw(TripState.AtShop("Woolworths Mordialloc"))

        // assert
        assertEquals("Woolworths Mordialloc", CurrentStop.trip.shop)
    }
}
