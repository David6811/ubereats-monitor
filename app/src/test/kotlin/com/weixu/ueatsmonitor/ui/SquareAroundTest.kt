package com.weixu.ueatsmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos

/**
 * A no-go box is drawn around where the driver is standing, because dragging a
 * rectangle on a map the size of a postcard is a worse way to say "not this
 * block" than standing in it.
 */
class SquareAroundTest {

    @Test
    fun `given a point in Melbourne, when a 400 metre box is drawn, then it is 400 metres tall`() {
        // arrange  Springvale, where a degree of latitude is 111.32 km
        val latitude = -37.95
        val longitude = 145.15

        // act
        val box = squareAround("Here", latitude, longitude, metres = 400)
        val tall = (box.north - box.south) * 111_320.0

        // assert
        assertEquals(400.0, tall, 1.0)
    }

    @Test
    fun `given the same box, when it is measured across, then it is 400 metres wide too`() {
        // arrange  a degree of longitude narrows towards the pole, and ignoring
        //          that made a box half as wide as it was tall
        val latitude = -37.95
        val longitude = 145.15

        // act
        val box = squareAround("Here", latitude, longitude, metres = 400)
        val wide = (box.east - box.west) * 111_320.0 * cos(Math.toRadians(latitude))

        // assert
        assertEquals(400.0, wide, 1.0)
    }

    @Test
    fun `given a box drawn around a point, when it is checked, then the point is inside it`() {
        // arrange
        val latitude = -37.95
        val longitude = 145.15

        // act
        val box = squareAround("Here", latitude, longitude, metres = 200)

        // assert
        assertEquals(true, latitude in box.south..box.north && longitude in box.west..box.east)
    }
}
