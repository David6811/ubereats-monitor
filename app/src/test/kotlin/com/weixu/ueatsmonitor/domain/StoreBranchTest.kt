package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Real rows from the bundled tables. The three Domino's are the whole of what
 * the table holds under that name, and the suburb points are the gazetteer's.
 */
class StoreBranchTest {

    private val dandenong = Store("Domino's", "fast_food", "STANDALONE_PARKING", GeoPoint(-37.9886, 145.2164))
    private val elsewhere = Store("Domino's", "fast_food", "STRIP", GeoPoint(-37.9655, 145.0554))
    private val mordialloc = Store("Domino's", "fast_food", "STRIP", GeoPoint(-38.0079, 145.0870))
    private val lone = Store("Charman Bakery", "bakery", "STRIP", GeoPoint(-37.9790, 145.0680))
    private val stores = listOf(dandenong, elsewhere, mordialloc, lone)

    private val suburbs = listOf(
        Suburb("Mordialloc", GeoPoint(-37.9994, 145.0961)),
        Suburb("Clayton", GeoPoint(-37.9158, 145.1314)),
        Suburb("Dandenong", GeoPoint(-37.9875, 145.2148)),
    )

    @Test
    fun `given a chain and the branch it names, when found, then that branch is the one`() {
        // arrange  the Mordialloc branch is 1.2km from the suburb's point
        val pickup = "Domino's - MORDIALLOC"

        // act
        val found = StoreKinds.find(pickup, stores, suburbs)

        // assert
        assertEquals(mordialloc, found)
    }

    @Test
    fun `given a chain and a different branch, when found, then that other branch is the one`() {
        // arrange
        val pickup = "Domino's (Dandenong)"

        // act
        val found = StoreKinds.find(pickup, stores, suburbs)

        // assert
        assertEquals(dandenong, found)
    }

    @Test
    fun `given a branch the table does not hold, when found, then the shop is not placed at all`() {
        // arrange  the nearest Domino's to Clayton is 8.7km away, so it is not Clayton's
        val pickup = "Domino's (Clayton)"

        // act
        val found = StoreKinds.find(pickup, stores, suburbs)

        // assert
        assertNull(found)
    }

    @Test
    fun `given a chain with nothing to tell the branches apart, when found, then it is not placed`() {
        // arrange
        val pickup = "Domino's"

        // act
        val found = StoreKinds.find(pickup, stores, suburbs)

        // assert
        assertNull(found)
    }

    @Test
    fun `given brackets naming a mall rather than a suburb, when found, then it is not placed`() {
        // arrange  "Parkmore" is a shopping centre; the gazetteer has no such suburb
        val pickup = "Domino's (Parkmore SC Store)"

        // act
        val found = StoreKinds.find(pickup, stores, suburbs)

        // assert
        assertNull(found)
    }

    @Test
    fun `given a shop the table holds once, when found, then the branch question does not arise`() {
        // arrange
        val pickup = "Charman Bakery"

        // act
        val found = StoreKinds.find(pickup, stores, suburbs)

        // assert
        assertEquals(lone, found)
    }

    @Test
    fun `given a caller with no gazetteer, when found, then the old answer stands`() {
        // arrange  StoreKinds.of has no suburbs to offer and must keep working
        val pickup = "Domino's - MORDIALLOC"

        // act
        val found = StoreKinds.find(pickup, stores)

        // assert
        assertEquals(dandenong, found)
    }
}
