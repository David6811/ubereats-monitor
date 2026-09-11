package com.weixu.ueatsmonitor.action

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {

    @Test
    fun `given an address with no state, when it is qualified, then Victoria is added`() {
        // arrange
        val place = "Douglas Street & Shepreth Avenue, Noble Park"

        // act
        val asked = Navigation.qualify(place)

        // assert
        assertEquals("Douglas Street & Shepreth Avenue, Noble Park, Victoria, Australia", asked)
    }

    @Test
    fun `given a shop with its suburb in brackets, when it is qualified, then the brackets go`() {
        // arrange
        val place = "Amritsari Sweets & Snacks (Noble Park)"

        // act
        val asked = Navigation.qualify(place)

        // assert
        assertEquals("Amritsari Sweets & Snacks Noble Park, Victoria, Australia", asked)
    }

    @Test
    fun `given an address that already names the state, when it is qualified, then it is left alone`() {
        // arrange
        val place = "12 Example Cres, Noble Park VIC 3174"

        // act
        val asked = Navigation.qualify(place)

        // assert
        assertEquals(place, asked)
    }
}
