package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaJudgeTest {

    @Test
    fun `given only suburbs inside the area, when judged, then the call is all inside`() {
        // arrange
        val found = listOf(KEYSBOROUGH, NOBLE_PARK)

        // act
        val call = AreaJudge.call(found, ServiceArea.SOUTH_EAST)

        // assert
        assertTrue(call is AreaCall.AllInside)
    }

    @Test
    fun `given one suburb outside the area, when judged, then that suburb is named`() {
        // arrange
        val found = listOf(SPRINGVALE, NOBLE_PARK)

        // act
        val call = AreaJudge.call(found, ServiceArea.SOUTH_EAST)

        // assert
        assertEquals(listOf("Springvale"), (call as AreaCall.SomeOutside).outside.map { it.name })
    }

    @Test
    fun `given one suburb outside the area, when judged, then the inside ones are still reported`() {
        // arrange
        val found = listOf(SPRINGVALE, NOBLE_PARK)

        // act
        val call = AreaJudge.call(found, ServiceArea.SOUTH_EAST) as AreaCall.SomeOutside

        // assert
        assertEquals(listOf("Noble Park"), call.inside.map { it.name })
    }

    @Test
    fun `given no suburb was recognised, when judged, then the area says nothing`() {
        // arrange
        val found = emptyList<Suburb>()

        // act
        val call = AreaJudge.call(found, ServiceArea.SOUTH_EAST)

        // assert
        assertEquals(AreaCall.NoSuburb, call)
    }

    @Test
    fun `given a name in different case, when checked, then the area still holds it`() {
        // arrange
        val suburb = Suburb("keysborough", GeoPoint(-38.0054, 145.1674))

        // act
        val holds = ServiceArea.SOUTH_EAST.holds(suburb)

        // assert
        assertTrue(holds)
    }

    @Test
    fun `given the south east area, when counted, then it holds the thirty two agreed suburbs`() {
        // arrange
        val area = ServiceArea.SOUTH_EAST

        // act
        val size = area.suburbNames.size

        // assert
        assertEquals(32, size)
    }

    private companion object {
        val KEYSBOROUGH = Suburb("Keysborough", GeoPoint(-38.0054, 145.1674))
        val NOBLE_PARK = Suburb("Noble Park", GeoPoint(-37.9695, 145.1767))
        val SPRINGVALE = Suburb("Springvale", GeoPoint(-37.9456, 145.158))
    }
}
