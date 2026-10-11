package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NoGoGeoJsonTest {

    private val dandenong = NoGoBox(label = "Dandenong", south = -38.0, west = 145.2, north = -37.9, east = 145.3)

    @Test
    fun `given a box still in force, when drawn, then it is a closed rectangle marked refusing`() {
        // arrange
        val boxes = listOf(dandenong)

        // act
        val json = NoGoGeoJson.featureCollection(boxes, off = emptySet())

        // assert
        assertEquals(
            """{"type":"FeatureCollection","features":[{"type":"Feature",""" +
                """"properties":{"label":"Dandenong","refusing":true},""" +
                """"geometry":{"type":"Polygon","coordinates":[[[145.2,-38.0],[145.3,-38.0],""" +
                """[145.3,-37.9],[145.2,-37.9],[145.2,-38.0]]]}}]}""",
            json,
        )
    }

    @Test
    fun `given a box switched off for this trip, when drawn, then it is still there but not refusing`() {
        // arrange
        val boxes = listOf(dandenong)

        // act
        val json = NoGoGeoJson.featureCollection(boxes, off = setOf("dandenong"))

        // assert
        assertEquals(true, json.contains(""""refusing":false"""))
    }
}
