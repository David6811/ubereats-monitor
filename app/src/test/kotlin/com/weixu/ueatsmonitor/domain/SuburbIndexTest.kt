package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SuburbIndexTest {

    @Test
    fun `given the gazetteer format, when parsed, then each row becomes a suburb`() {
        // arrange
        val csv = GAZETTEER

        // act
        val suburbs = SuburbIndex.parse(csv)

        // assert
        assertEquals(4, suburbs.size)
    }

    @Test
    fun `given a comment line, when parsed, then it is skipped`() {
        // arrange
        val csv = "# name,lat,lon\nNoble Park,-37.9695,145.1767\n"

        // act
        val suburbs = SuburbIndex.parse(csv)

        // assert
        assertEquals(listOf("Noble Park"), suburbs.map { it.name })
    }

    @Test
    fun `given a malformed row, when parsed, then it is dropped instead of crashing`() {
        // arrange
        val csv = "Noble Park,-37.9695,145.1767\nBroken Row,not-a-number,145.0\n"

        // act
        val suburbs = SuburbIndex.parse(csv)

        // assert
        assertEquals(listOf("Noble Park"), suburbs.map { it.name })
    }

    @Test
    fun `given a screen naming one suburb, when searched, then that suburb is found`() {
        // arrange
        val text = "Deliver to 12 Chandler Rd, Keysborough VIC 3173"

        // act
        val found = SuburbIndex.findAll(text, SuburbIndex.parse(GAZETTEER))

        // assert
        assertEquals(listOf("Keysborough"), found.map { it.name })
    }

    @Test
    fun `given a longer suburb name, when searched, then the shorter name inside it is not reported`() {
        // arrange
        val text = "Pickup in Noble Park North"

        // act
        val found = SuburbIndex.findAll(text, SuburbIndex.parse(GAZETTEER))

        // assert
        assertEquals(listOf("Noble Park North"), found.map { it.name })
    }

    @Test
    fun `given a screen naming two suburbs, when searched, then both are found longest first`() {
        // arrange
        val text = "McDonald's Dandenong → 8 Fern St, Keysborough"

        // act
        val found = SuburbIndex.findAll(text, SuburbIndex.parse(GAZETTEER))

        // assert
        assertEquals(listOf("Keysborough", "Dandenong"), found.map { it.name })
    }

    private companion object {
        val GAZETTEER = """
            # name,lat,lon
            Dandenong,-37.9875,145.2148
            Keysborough,-38.0054,145.1674
            Noble Park,-37.9695,145.1767
            Noble Park North,-37.9494,145.1889
        """.trimIndent()
    }
}
