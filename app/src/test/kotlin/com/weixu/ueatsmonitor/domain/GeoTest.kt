package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {

    @Test
    fun `given noble park and keysborough, when bearing is taken, then it points just west of south`() {
        // arrange
        val from = NOBLE_PARK
        val to = KEYSBOROUGH

        // act
        val bearing = Geo.bearingDegrees(from, to)

        // assert
        assertEquals(191.5367, bearing, 0.001)
    }

    @Test
    fun `given noble park and keysborough, when distance is taken, then it is two and a half miles`() {
        // arrange
        val from = NOBLE_PARK
        val to = KEYSBOROUGH

        // act
        val distance = Geo.straightLine(from, to)

        // assert
        assertEquals(2.5316, distance.value, 0.001)
    }

    @Test
    fun `given noble park and dandenong, when bearing is taken, then it points south east`() {
        // arrange
        val from = NOBLE_PARK
        val to = DANDENONG

        // act
        val heading = Geo.headingTo(from, to)

        // assert
        assertEquals(Compass.SE, heading.compass)
    }

    @Test
    fun `given noble park and the cbd, when bearing is taken, then it points north west`() {
        // arrange
        val from = NOBLE_PARK
        val to = MELBOURNE_CBD

        // act
        val heading = Geo.headingTo(from, to)

        // assert
        assertEquals(Compass.NW, heading.compass)
    }

    @Test
    fun `given noble park and the cbd, when distance is taken, then it is sixteen miles`() {
        // arrange
        val from = NOBLE_PARK
        val to = MELBOURNE_CBD

        // act
        val distance = Geo.straightLine(from, to)

        // assert
        assertEquals(15.8644, distance.value, 0.01)
    }

    @Test
    fun `given a bearing just short of due north, when named, then it rounds back to north`() {
        // arrange
        val bearing = 350.0

        // act
        val compass = Geo.compassOf(bearing)

        // assert
        assertEquals(Compass.N, compass)
    }

    @Test
    fun `given a bearing of exactly south west, when named, then it is south west`() {
        // arrange
        val bearing = 225.0

        // act
        val compass = Geo.compassOf(bearing)

        // assert
        assertEquals(Compass.SW, compass)
    }

    private companion object {
        val NOBLE_PARK = GeoPoint(-37.9695, 145.1767)
        val KEYSBOROUGH = GeoPoint(-38.0054, 145.1674)
        val DANDENONG = GeoPoint(-37.9875, 145.2148)
        val MELBOURNE_CBD = GeoPoint(-37.8136, 144.9631)
    }
}
