package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CentreChoiceTest {

    private val setCentre = GeoPoint(-37.99, 145.16)
    private val depot = GeoPoint(-38.02, 145.12)

    @Test
    fun `given a temporary point set today, when the centre is chosen, then it wins over the set's`() {
        // arrange
        val temp = TempCentre("Wells Rd", depot, day = "2026-10-11")

        // act
        val centre = CentreChoice.inForce(temp, today = "2026-10-11", setCentre = setCentre)

        // assert
        assertEquals(depot, centre)
    }

    @Test
    fun `given a temporary point from yesterday, when the centre is chosen, then the set's centre is back`() {
        // arrange
        val temp = TempCentre("Wells Rd", depot, day = "2026-10-10")

        // act
        val centre = CentreChoice.inForce(temp, today = "2026-10-11", setCentre = setCentre)

        // assert
        assertEquals(setCentre, centre)
    }

    @Test
    fun `given no temporary point, when the centre is chosen, then it is the set's`() {
        // arrange
        val temp: TempCentre? = null

        // act
        val centre = CentreChoice.inForce(temp, today = "2026-10-11", setCentre = setCentre)

        // assert
        assertEquals(setCentre, centre)
    }

    @Test
    fun `given a set with no centre and a point set today, when the centre is chosen, then the point is used`() {
        // arrange
        val temp = TempCentre("Wells Rd", depot, day = "2026-10-11")

        // act
        val centre = CentreChoice.inForce(temp, today = "2026-10-11", setCentre = null)

        // assert
        assertEquals(depot, centre)
    }
}
