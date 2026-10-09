package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SuburbGeoJsonTest {

    private val triangle = listOf(GeoPoint(-37.0, 145.0), GeoPoint(-37.1, 145.1), GeoPoint(-37.0, 145.0))

    @Test
    fun `given one chosen suburb, when drawn, then it is a chosen feature with lon-lat positions`() {
        // arrange
        val shapes = listOf(SuburbShape("Rosanna", listOf(triangle)))

        // act
        val json = SuburbGeoJson.featureCollection(shapes, chosen = setOf("Rosanna"))

        // assert
        assertEquals(
            """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"name":"Rosanna","chosen":true},""" +
                """"geometry":{"type":"MultiPolygon","coordinates":[[[[145.0,-37.0],[145.1,-37.1],[145.0,-37.0]]]]}}]}""",
            json,
        )
    }

    @Test
    fun `given a suburb outside the set, when drawn, then it is still drawn but not chosen`() {
        // arrange
        val shapes = listOf(SuburbShape("Ivanhoe", listOf(triangle)))

        // act
        val json = SuburbGeoJson.featureCollection(shapes, chosen = setOf("Rosanna"))

        // assert
        assertEquals(true, json.contains(""""properties":{"name":"Ivanhoe","chosen":false}"""))
    }
}
