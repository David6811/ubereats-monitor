package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuburbAtTest {

    /** Two squares side by side, a degree each, west and east. */
    private val shapes = listOf(
        SuburbShape("West", listOf(square(minLon = 145.0, maxLon = 145.1))),
        SuburbShape("East", listOf(square(minLon = 145.1, maxLon = 145.2))),
    )

    @Test
    fun `given a point inside one square, when the suburb is asked for, then that square answers`() {
        // arrange
        val point = GeoPoint(-37.95, 145.15)

        // act
        val name = SuburbAt.find(point, shapes)

        // assert
        assertEquals("East", name)
    }

    @Test
    fun `given a point outside both squares, when the suburb is asked for, then nothing answers`() {
        // arrange
        val point = GeoPoint(-37.95, 145.4)

        // act
        val name = SuburbAt.find(point, shapes)

        // assert
        assertNull(name)
    }

    private fun square(minLon: Double, maxLon: Double): List<GeoPoint> = listOf(
        GeoPoint(-38.0, minLon),
        GeoPoint(-38.0, maxLon),
        GeoPoint(-37.9, maxLon),
        GeoPoint(-37.9, minLon),
    )
}
