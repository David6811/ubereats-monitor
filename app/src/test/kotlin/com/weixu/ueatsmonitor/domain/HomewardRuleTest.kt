package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomewardRuleTest {

    @Test
    fun `given a drop nearer the centre than the car, when judged, then it is closer`() {
        // arrange  the car is out at Rowville, the drop is in Keysborough, the centre is Aspendale Gardens
        val call = HomewardRule.judge(carAt = ROWVILLE, dropAt = KEYSBOROUGH, centre = CENTRE)

        // act
        val closer = call is Homeward.Closer

        // assert
        assertTrue(closer)
    }

    @Test
    fun `given a drop further out than the car, when judged, then it is further`() {
        // arrange
        val call = HomewardRule.judge(carAt = KEYSBOROUGH, dropAt = ROWVILLE, centre = CENTRE)

        // act
        val further = call is Homeward.Further

        // assert
        assertTrue(further)
    }

    @Test
    fun `given no position for the car, when judged, then it says nothing`() {
        // arrange
        val call = HomewardRule.judge(carAt = null, dropAt = ROWVILLE, centre = CENTRE)

        // act
        val answer = call

        // assert
        assertEquals(Homeward.Unknown, answer)
    }

    @Test
    fun `given no centre drawn for the set, when judged, then it says nothing`() {
        // arrange
        val call = HomewardRule.judge(carAt = KEYSBOROUGH, dropAt = ROWVILLE, centre = null)

        // act
        val answer = call

        // assert
        assertEquals(Homeward.Unknown, answer)
    }

    private companion object {
        /** Wells Rd, Aspendale Gardens - the centre drawn for the driver's own set. */
        val CENTRE = GeoPoint(-38.02506, 145.12873)
        val KEYSBOROUGH = GeoPoint(-38.0054, 145.1674)
        val ROWVILLE = GeoPoint(-37.9282, 145.2333)
    }
}
