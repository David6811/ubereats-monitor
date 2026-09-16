package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OfferRunTest {

    private data class Frame(val atMillis: Long, val offer: OfferRecord?)

    private val pizza = OfferRecord(
        isMatch = false,
        payout = "${'$'}5.00",
        pickup = "Mario's Pizza And Pasta",
        dropoff = "Cole Street, Noble Park",
        ruling = "可以接单",
        why = "Noble Park 在名单里",
        fromTree = false,
    )

    private val kebab = pizza.copy(pickup = "Ali Baba Kebabs", payout = "${'$'}9.20")

    private fun collapse(frames: List<Frame>): List<Frame> =
        OfferRun.collapse(frames, Frame::atMillis, Frame::offer)

    @Test
    fun `given the same card on many frames, when they are collapsed, then one record comes back`() {
        // arrange
        val frames = listOf(
            Frame(60_000, pizza),
            Frame(58_000, pizza),
            Frame(56_000, pizza),
        )

        // act
        val runs = collapse(frames)

        // assert
        assertEquals(listOf(Frame(60_000, pizza)), runs)
    }

    @Test
    fun `given two different cards, when they are collapsed, then both are kept`() {
        // arrange
        val frames = listOf(
            Frame(60_000, kebab),
            Frame(58_000, pizza),
            Frame(56_000, pizza),
        )

        // act
        val runs = collapse(frames)

        // assert
        assertEquals(listOf(Frame(60_000, kebab), Frame(58_000, pizza)), runs)
    }

    @Test
    fun `given the same card again hours later, when they are collapsed, then it counts as a second offer`() {
        // arrange
        val frames = listOf(
            Frame(4_000_000, pizza),
            Frame(60_000, pizza),
        )

        // act
        val runs = collapse(frames)

        // assert
        assertEquals(frames, runs)
    }

    @Test
    fun `given OCR reading the shop name differently on two frames, when they are collapsed, then it stays one offer`() {
        // arrange
        val frames = listOf(
            Frame(60_000, pizza.copy(pickup = "9 Mario's Pizza And Pasta")),
            Frame(58_000, pizza.copy(pickup = "p Mario's Pizza And Pasta")),
        )

        // act
        val runs = collapse(frames)

        // assert
        assertEquals(listOf(frames.first()), runs)
    }

    @Test
    fun `given a frame with no card between two of the same card, when they are collapsed, then the run is broken`() {
        // arrange
        val frames = listOf(
            Frame(60_000, pizza),
            Frame(58_000, null),
            Frame(56_000, pizza),
        )

        // act
        val runs = collapse(frames)

        // assert
        assertEquals(frames, runs)
    }
}
