package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MapProjectionBackTest {

    private val box = GeoBox(minLat = -38.05, maxLat = -37.90, minLon = 145.05, maxLon = 145.25)
    private val fit = MapProjection.fit(box, width = 900f, height = 600f, padding = 12f)

    @Test
    fun `given a place on the drawing, when it is placed and read back, then the same point comes out`() {
        // arrange  a corner of Keysborough, well inside the box
        val point = GeoPoint(-38.0054, 145.1674)

        // act
        val back = fit.placeOf(fit.place(point))

        // assert
        // a tenth of a metre, which is all a float pixel can carry
        assertEquals(point.latitude, back.latitude, 1e-6)
        assertEquals(point.longitude, back.longitude, 1e-6)
    }

    @Test
    fun `given the drawing's top left corner of the box, when it is read back, then it is the box's north-west`() {
        // arrange
        val northWest = GeoPoint(box.maxLat, box.minLon)

        // act
        val back = fit.placeOf(fit.place(northWest))

        // assert
        assertEquals(box.minLon, back.longitude, 1e-6)
    }
}
