package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreKindsTest {

    private val csv = """
        # name,lat,lon,kind,setting,parking_m,parking_type,neighbours_60m
        Coles,-37.94,145.15,supermarket,MALL,0,underground,12
        Coles Express,-37.95,145.16,fuel,STANDALONE_PARKING,10,surface,0
        Amritsari Sweets & Snacks,-37.96,145.17,restaurant,STRIP,80,street_side,6
    """.trimIndent()

    private val stores = StoreKinds.parse(csv)

    @Test
    fun `given a pickup line OCR padded, when its kind is looked up, then the shop is still found`() {
        // arrange
        val pickup = "9 Amritsari Sweets & Snacks (Noble Park)"

        // act
        val kind = StoreKinds.of(pickup, stores)

        // assert
        assertEquals("restaurant", kind)
    }

    @Test
    fun `given a shop whose name contains a shorter shop's name, when its kind is looked up, then the longer name wins`() {
        // arrange
        val pickup = "Coles Express (Springvale)"

        // act
        val kind = StoreKinds.of(pickup, stores)

        // assert
        assertEquals("fuel", kind)
    }

    @Test
    fun `given a shop not in the table, when its kind is looked up, then nothing comes back`() {
        // arrange
        val pickup = "Mario's Pizza And Pasta"

        // act
        val kind = StoreKinds.of(pickup, stores)

        // assert
        assertNull(kind)
    }
}
