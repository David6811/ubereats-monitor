package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreKindsTest {

    private val csv = """
        # name,lat,lon,kind,setting,parking_m,parking_type,neighbours_60m
        Coles,-37.94,145.15,supermarket,MALL,0,underground,12
        Coles Express,-37.95,145.16,fuel,STANDALONE_PARKING,10,surface,0
        Nando's,-38.00,145.11,restaurant,STANDALONE_PARKING,20,surface,1
        Amritsari Sweets & Snacks,-37.96,145.17,restaurant,STRIP,80,street_side,6
    """.trimIndent()

    private val stores = StoreKinds.parse(csv)

    @Test
    fun `given a pickup line OCR padded, when the shop is looked up, then it is still found`() {
        // arrange
        val pickup = "9 Amritsari Sweets & Snacks (Noble Park)"

        // act
        val store = StoreKinds.find(pickup, stores)

        // assert
        assertEquals("Amritsari Sweets & Snacks", store?.name)
    }

    @Test
    fun `given a shop whose name contains a shorter shop's name, when it is looked up, then the longer name wins`() {
        // arrange
        val pickup = "Coles Express (Springvale)"

        // act
        val store = StoreKinds.find(pickup, stores)

        // assert
        assertEquals("Coles Express", store?.name)
    }

    @Test
    fun `given a name OCR spelled wrong, when the shop is looked up, then the near name is taken`() {
        // arrange  the shopfront read as "Nandos" and once as "Nandoe"
        val pickup = "Nandos (Braecide)"

        // act
        val store = StoreKinds.find(pickup, stores)

        // assert
        assertEquals("Nando's", store?.name)
    }

    @Test
    fun `given a name two letters wrong, when the shop is looked up, then the near name is still taken`() {
        // arrange
        val pickup = "Nandoe (Braecide)"

        // act
        val store = StoreKinds.find(pickup, stores)

        // assert
        assertEquals("Nando's", store?.name)
    }

    @Test
    fun `given a short shop name a letter from something in the line, when it is looked up, then it is not taken`() {
        // arrange  "Asia" is one edit from the "asta" inside this pickup
        val short = StoreKinds.parse("Asia,-37.94,145.15,restaurant,MALL,0,underground,3")

        // act
        val store = StoreKinds.find("Mario's Pizza And Pasta", short)

        // assert
        assertNull(store)
    }

    @Test
    fun `given a shop not in the table, when it is looked up, then nothing comes back`() {
        // arrange
        val pickup = "Mario's Pizza And Pasta"

        // act
        val store = StoreKinds.find(pickup, stores)

        // assert
        assertNull(store)
    }

    @Test
    fun `given a shop in a mall, when its setting is put in words, then it says so`() {
        // arrange
        val store = StoreKinds.find("Coles (Springvale)", stores)

        // act
        val where = store?.setting?.let { StoreKinds.where(it, Lang.CHINESE) }

        // assert
        assertEquals("商场", where)
    }
}
