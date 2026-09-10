package com.weixu.ueatsmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class JobBoardTest {

    private val pizza = OfferRecord(
        isMatch = false,
        payout = "${'$'}5.00",
        pickup = "Mario's Pizza And Pasta",
        dropoff = "Cole Street, Noble Park",
        ruling = "可以接单",
        why = "Noble Park 在名单里",
    )

    private val kebab = OfferRecord(
        isMatch = false,
        payout = "${'$'}9.20",
        pickup = "Ali Baba Kebabs",
        dropoff = "Corrigan Road, Noble Park",
        ruling = "可以接单",
        why = "Noble Park 在名单里",
    )

    @Test
    fun `given a job on the board, when a second one arrives, then both are held`() {
        // arrange
        val board = listOf(Job(1_000, pizza))

        // act
        val next = JobBoard.add(board, Job(2_000, kebab))

        // assert  newest first
        assertEquals(listOf(Job(2_000, kebab), Job(1_000, pizza)), next)
    }

    @Test
    fun `given the same card read again, when it is added, then the board does not change`() {
        // arrange
        val board = listOf(Job(1_000, pizza))

        // act
        val next = JobBoard.add(board, Job(3_000, pizza.copy(pickup = "9 Mario's Pizza And Pasta")))

        // assert
        assertEquals(board, next)
    }

    @Test
    fun `given a full board, when one more arrives, then the oldest falls off`() {
        // arrange
        val board = (1..JobBoard.CAPACITY).map { at ->
            Job(at.toLong(), pizza.copy(dropoff = "Street $at"))
        }.reversed()

        // act
        val next = JobBoard.add(board, Job(99, kebab))

        // affirm
        assertEquals(JobBoard.CAPACITY, next.size)

        // assert  the one from the far end is gone
        assertEquals(listOf<Long>(99, 6, 5, 4, 3, 2), next.map { it.atMillis })
    }

    @Test
    fun `given two jobs, when one is cleared, then the other stays`() {
        // arrange
        val board = listOf(Job(2_000, kebab), Job(1_000, pizza))

        // act
        val next = JobBoard.remove(board, 2_000)

        // assert
        assertEquals(listOf(Job(1_000, pizza)), next)
    }
}
