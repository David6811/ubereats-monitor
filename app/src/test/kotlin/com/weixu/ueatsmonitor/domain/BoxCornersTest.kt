package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxCornersTest {

    private val box = NoGoBox(label = "Dandenong", south = -38.0, west = 145.2, north = -37.9, east = 145.3)

    @Test
    fun `given the south-west corner, when dragged out, then only that corner moves`() {
        // arrange
        val corner = BoxCorners.SOUTH_WEST

        // act
        val moved = BoxCorners.moved(box, corner, lat = -38.1, lon = 145.1)

        // assert
        assertEquals(NoGoBox("Dandenong", south = -38.1, west = 145.1, north = -37.9, east = 145.3), moved)
    }

    @Test
    fun `given the north-east corner, when dragged out, then the far two edges stay put`() {
        // arrange
        val corner = BoxCorners.NORTH_EAST

        // act
        val moved = BoxCorners.moved(box, corner, lat = -37.8, lon = 145.4)

        // assert
        assertEquals(NoGoBox("Dandenong", south = -38.0, west = 145.2, north = -37.8, east = 145.4), moved)
    }

    @Test
    fun `given a corner dragged past the far side, when moved, then the box is not inside out`() {
        // arrange
        val corner = BoxCorners.SOUTH_WEST

        // act
        val moved = BoxCorners.moved(box, corner, lat = -37.5, lon = 145.9)

        // assert
        assertEquals(NoGoBox("Dandenong", south = -37.9, west = 145.3, north = -37.5, east = 145.9), moved)
    }
}
