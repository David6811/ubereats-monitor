package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SuburbPickerTest {

    private val all = listOf(
        "Noble Park", "Noble Park North", "Springvale", "Springvale South", "Parkdale",
    )

    @Test
    fun `given nothing typed, when rows are asked for, then only the chosen suburbs come back`() {
        // arrange
        val chosen = setOf("Springvale South", "Noble Park")

        // act
        val rows = SuburbPicker.rows(all, chosen, "")

        // affirm
        assertEquals(true, rows.all { it.chosen })

        // assert
        assertEquals(listOf("Noble Park", "Springvale South"), rows.map { it.name })
    }

    @Test
    fun `given a query, when rows are asked for, then names starting with it come before names merely containing it`() {
        // arrange
        val chosen = setOf<String>()

        // act
        val rows = SuburbPicker.rows(all, chosen, "park")

        // assert
        assertEquals(listOf("Parkdale", "Noble Park", "Noble Park North"), rows.map { it.name })
    }

    @Test
    fun `given a searched suburb already chosen, when rows are asked for, then it comes back ticked`() {
        // arrange
        val chosen = setOf("Noble Park")

        // act
        val rows = SuburbPicker.rows(all, chosen, "noble")

        // assert
        assertEquals(listOf(SuburbRow("Noble Park", true), SuburbRow("Noble Park North", false)), rows)
    }

    @Test
    fun `given a chosen suburb, when it is toggled, then it is dropped from the set`() {
        // arrange
        val chosen = setOf("Noble Park", "Parkdale")

        // act
        val next = SuburbPicker.toggle(chosen, "Parkdale")

        // assert
        assertEquals(setOf("Noble Park"), next)
    }

    @Test
    fun `given a suburb that is not chosen, when it is toggled, then it joins the set`() {
        // arrange
        val chosen = setOf("Noble Park")

        // act
        val next = SuburbPicker.toggle(chosen, "Springvale")

        // assert
        assertEquals(setOf("Noble Park", "Springvale"), next)
    }
}
