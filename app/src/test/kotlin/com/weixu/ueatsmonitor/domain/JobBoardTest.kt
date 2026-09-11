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
        val board = listOf(Job(1_000, pizza, taken = false, address = null, note = null))

        // act
        val next = JobBoard.add(board, Job(2_000, kebab, taken = false, address = null, note = null))

        // assert  newest first
        assertEquals(listOf(Job(2_000, kebab, taken = false, address = null, note = null), Job(1_000, pizza, taken = false, address = null, note = null)), next)
    }

    @Test
    fun `given the same card read again, when it is added, then the board does not change`() {
        // arrange
        val board = listOf(Job(1_000, pizza, taken = false, address = null, note = null))

        // act
        val next = JobBoard.add(board, Job(3_000, pizza.copy(pickup = "9 Mario's Pizza And Pasta"), taken = false, address = null, note = null))

        // assert
        assertEquals(board, next)
    }

    @Test
    fun `given a full board, when one more arrives, then the oldest falls off`() {
        // arrange
        val board = (1..JobBoard.CAPACITY).map { at ->
            Job(at.toLong(), pizza.copy(dropoff = "Street $at"), taken = false, address = null, note = null)
        }.reversed()

        // act
        val next = JobBoard.add(board, Job(99, kebab, taken = false, address = null, note = null))

        // affirm
        assertEquals(JobBoard.CAPACITY, next.size)

        // assert  the one from the far end is gone
        assertEquals(listOf<Long>(99, 6, 5, 4, 3, 2), next.map { it.atMillis })
    }

    @Test
    fun `given two jobs, when one is cleared, then the other stays`() {
        // arrange
        val board = listOf(Job(2_000, kebab, taken = false, address = null, note = null), Job(1_000, pizza, taken = false, address = null, note = null))

        // act
        val next = JobBoard.remove(board, 2_000)

        // assert
        assertEquals(listOf(Job(1_000, pizza, taken = false, address = null, note = null)), next)
    }

    @Test
    fun `given a job the rules would take, when it is shelved, then it is worth taking`() {
        // arrange
        val job = Job(1_000, pizza, taken = false, address = null, note = null)

        // act
        val shelf = Shelf.of(job)

        // assert
        assertEquals(Shelf.WORTH_TAKING, shelf)
    }

    @Test
    fun `given a job the rules refuse, when it is shelved, then it is not worth taking`() {
        // arrange
        val job = Job(1_000, pizza.copy(ruling = "不要接单"), taken = false, address = null, note = null)

        // act
        val shelf = Shelf.of(job)

        // assert
        assertEquals(Shelf.NOT_WORTH_TAKING, shelf)
    }

    @Test
    fun `given a job the rules refused but he took anyway, when it is shelved, then it is on the taken shelf`() {
        // arrange
        val job = Job(1_000, pizza.copy(ruling = "不要接单"), taken = true, address = null, note = null)

        // act
        val shelf = Shelf.of(job)

        // assert
        assertEquals(Shelf.TAKEN, shelf)
    }

    @Test
    fun `given a job read before the rules ruled, when it is shelved, then it is not worth taking`() {
        // arrange
        val job = Job(1_000, pizza.copy(ruling = null), taken = false, address = null, note = null)

        // act
        val shelf = Shelf.of(job)

        // assert
        assertEquals(Shelf.NOT_WORTH_TAKING, shelf)
    }

    @Test
    fun `given the pickup screen for a job on the board, when it is applied, then that job is taken`() {
        // arrange
        val board = listOf(
            Job(2_000, kebab, taken = false, address = null, note = null),
            Job(1_000, pizza.copy(pickup = "9 Guzman y Gomez (Springvale)"), taken = false, address = null, note = null),
        )
        val pickup = Pickup(
            store = "Guzman y Gomez",
            address = "278 Centre Dandenong Rd, Dingley Village VIC 3172, Australia",
            note = "Dedicated GYG car park",
        )

        // act
        val next = JobBoard.taken(board, pickup)

        // affirm  the other job is untouched
        assertEquals(false, next[0].taken)

        // assert
        assertEquals(
            Triple(true, pickup.address, pickup.note),
            Triple(next[1].taken, next[1].address, next[1].note),
        )
    }

    @Test
    fun `given a pickup for a shop not on the board, when it is applied, then nothing changes`() {
        // arrange
        val board = listOf(Job(1_000, pizza, taken = false, address = null, note = null))
        val pickup = Pickup(store = "Nando's", address = "1 High St, Braeside VIC 3195", note = null)

        // act
        val next = JobBoard.taken(board, pickup)

        // assert
        assertEquals(board, next)
    }
}
