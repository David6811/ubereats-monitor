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
        fromTree = false,
    )

    private val NOW = 1_789_603_800_000L
    private val HALF_AN_HOUR_AGO = NOW - 30L * 60 * 1000
    private val DAY_BEFORE = NOW - 40L * 60 * 60 * 1000

    private val kebab = OfferRecord(
        isMatch = false,
        payout = "${'$'}9.20",
        pickup = "Ali Baba Kebabs",
        dropoff = "Corrigan Road, Noble Park",
        ruling = "可以接单",
        why = "Noble Park 在名单里",
        fromTree = false,
    )

    @Test
    fun `given a job on the board, when a second one arrives, then both are held`() {
        // arrange
        val board = listOf(Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // act
        val next = JobBoard.add(board, Job(2_000, kebab, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // assert  newest first
        assertEquals(listOf(Job(2_000, kebab, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1), Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)), next)
    }

    @Test
    fun `given the same card read again, when it is added, then the board does not change`() {
        // arrange
        val board = listOf(Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // act
        val next = JobBoard.add(board, Job(3_000, pizza.copy(pickup = "9 Mario's Pizza And Pasta"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // assert
        assertEquals(board, next)
    }

    @Test
    fun `given a card whose address OCR read differently, when it is added, then the board does not change`() {
        // arrange
        val board = listOf(Job(1_789_181_933_388, pizza.copy(dropoff = "a Ashleigh Street & Jean Court, sb Keysborough"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // act
        val next = JobBoard.add(board, Job(1_789_181_938_491, pizza.copy(dropoff = "Ashleigh Street & Jean Court, Keysborough J./KTT|T3.T0"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // assert
        assertEquals(board, next)
    }

    @Test
    fun `given the same money again an hour later, when it is added, then it is a second job`() {
        // arrange
        val board = listOf(Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // act
        val next = JobBoard.add(board, Job(3_601_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // assert
        assertEquals(listOf<Long>(3_601_000, 1_000), next.map { it.atMillis })
    }

    @Test
    fun `given twenty offers on the board, when one more arrives, then none falls off`() {
        // arrange
        val board = (20 downTo 1).map { at ->
            Job(at.toLong(), pizza.copy(dropoff = "Street $at"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)
        }

        // act
        val next = JobBoard.add(board, Job(99, kebab, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // assert  20 already there + the new one
        assertEquals(21, next.size)
    }

    @Test
    fun `given an offer first read off the picture, when the tree reads it, then the tree's words replace it`() {
        // arrange  the card of 16 Sep 12:23, as OCR read it and as the tree did two seconds later
        val seen = OfferRecord(
            isMatch = false, payout = "${'$'}10.45", pickup = "9 Royal Stacks (Moorabbin)",
            dropoff = "o Latrobe Street & Phillip Street, Mentone",
            ruling = "不要接单", why = "Mentone 不在名单里", fromTree = false,
        )
        val read = seen.copy(
            pickup = "Royal Stacks (Moorabbin)",
            dropoff = "Latrobe Street & Phillip Street, Mentone",
            fromTree = true,
        )
        val board = listOf(Job(1_000, seen, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // act
        val next = JobBoard.add(board, Job(5_000, read, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // affirm  still one job, keeping the time it first appeared
        assertEquals(listOf<Long>(1_000), next.map { it.atMillis })

        // assert
        assertEquals("Latrobe Street & Phillip Street, Mentone", next.first().offer.dropoff)
    }

    @Test
    fun `given the money read wrongly off the picture, when the tree reads the same card, then it is not a second job`() {
        // arrange  "${'$'}8.06" came off the picture as "${'$'}8.O6"
        val seen = pizza.copy(payout = "${'$'}8.O6", dropoff = "Nepean Hwy, Moorabbin", fromTree = false)
        val read = pizza.copy(payout = "${'$'}8.06", dropoff = "Nepean Hwy, Moorabbin", fromTree = true)
        val board = listOf(Job(1_000, seen, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // act
        val next = JobBoard.add(board, Job(3_000, read, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // assert
        assertEquals(listOf("${'$'}8.06"), next.map { it.offer.payout })
    }

    @Test
    fun `given a job the tree already read, when the picture reads it again, then the tree's words stay`() {
        // arrange
        val read = pizza.copy(pickup = "Royal Stacks (Moorabbin)", fromTree = true)
        val seen = pizza.copy(pickup = "9 Royal Stacks (Moorabbin)", fromTree = false)
        val board = listOf(Job(1_000, read, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // act
        val next = JobBoard.add(board, Job(3_000, seen, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // assert
        assertEquals(listOf("Royal Stacks (Moorabbin)"), next.map { it.offer.pickup })
    }

    @Test
    fun `given a delivery screen that names the city after the suburb, when it is read, then the job still matches`() {
        // arrange  the card of 16 Sep 10:40 said Clarinda; the delivery screen said "Clarinda Melbourne VIC"
        val board = listOf(
            Job(1_000, pizza.copy(dropoff = "Bourke Road & Glenelg Drive, Clarinda"), taken = true, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
        )
        val dropoff = Dropoff(customer = "Elisa N.", address = "10 Bushland Avenue, Clarinda Melbourne VIC", unit = null, note = null)

        // act
        val next = JobBoard.delivered(board, dropoff, "Clarinda Melbourne", now = 2_000)

        // assert
        assertEquals("10 Bushland Avenue, Clarinda Melbourne VIC", next.first().dropAddress)
    }

    @Test
    fun `given a job to the same suburb taken two days ago, when today's delivery screen is read, then today's job takes it`() {
        // arrange  the board of 17 Sep 10:10: $4.00 was taken on the 15th, $9.15 is today's
        val old = Job(
            DAY_BEFORE, pizza.copy(payout = "${'$'}4.00", dropoff = "Dandenong Road & Reilly Street, Springvale"),
            taken = true, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null,
            extraDrops = emptyList(), ordersAtPickup = 1,
        )
        val today = Job(
            HALF_AN_HOUR_AGO, pizza.copy(payout = "${'$'}9.15", dropoff = "Donald Street & Hanleth Avenue, Springvale"),
            taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null,
            extraDrops = emptyList(), ordersAtPickup = 1,
        )
        val dropoff = Dropoff(customer = "SANDIL P.", address = "5 Ethel Court, Springvale Melbourne VIC", unit = null, note = null)

        // act
        val next = JobBoard.delivered(listOf(today, old), dropoff, "Springvale Melbourne", now = NOW)

        // affirm  the two-day-old job is untouched
        assertEquals(null, next.first { it.offer.payout == "${'$'}4.00" }.dropAddress)

        // assert
        assertEquals("5 Ethel Court, Springvale Melbourne VIC", next.first { it.offer.payout == "${'$'}9.15" }.dropAddress)
    }

    @Test
    fun `given a delivery in a suburb no job names, when it is read, then no job is touched`() {
        // arrange
        val board = listOf(
            Job(1_000, pizza.copy(dropoff = "Cole Street, Noble Park"), taken = true, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
        )
        val dropoff = Dropoff(customer = null, address = "1 Somewhere St, Frankston VIC", unit = null, note = null)

        // act
        val next = JobBoard.delivered(board, dropoff, "Frankston", now = 2_000)

        // assert
        assertEquals(board, next)
    }

    @Test
    fun `given two jobs, when one is cleared, then the other stays`() {
        // arrange
        val board = listOf(Job(2_000, kebab, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1), Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // act
        val next = JobBoard.remove(board, 2_000)

        // assert
        assertEquals(listOf(Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)), next)
    }

    @Test
    fun `given a job the rules would take, when the worth-taking shelf is asked, then it holds it`() {
        // arrange
        val job = Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)

        // act
        val held = Shelf.WORTH_TAKING.holds(job)

        // assert
        assertEquals(true, held)
    }

    @Test
    fun `given a job the rules refuse, when the not-worth-taking shelf is asked, then it holds it`() {
        // arrange
        val job = Job(1_000, pizza.copy(ruling = "不要接单"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)

        // act
        val held = Shelf.NOT_WORTH_TAKING.holds(job)

        // assert
        assertEquals(true, held)
    }

    @Test
    fun `given a job the rules refused but he took anyway, when every shelf is asked, then both the taken and the refused one hold it`() {
        // arrange
        val job = Job(1_000, pizza.copy(ruling = "不要接单"), taken = true, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)

        // act
        val held = Shelf.values().filter { it.holds(job) }

        // assert  the advice stays visible beside the fact that he took it
        assertEquals(listOf(Shelf.TAKEN, Shelf.NOT_WORTH_TAKING), held)
    }

    @Test
    fun `given a job the rules would take and he took, when every shelf is asked, then both the taken and the worth-taking one hold it`() {
        // arrange
        val job = Job(1_000, pizza, taken = true, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)

        // act
        val held = Shelf.values().filter { it.holds(job) }

        // assert
        assertEquals(listOf(Shelf.TAKEN, Shelf.WORTH_TAKING), held)
    }

    @Test
    fun `given a job nobody took, when the taken shelf is asked, then it does not hold it`() {
        // arrange
        val job = Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)

        // act
        val held = Shelf.TAKEN.holds(job)

        // assert
        assertEquals(false, held)
    }

    @Test
    fun `given a job read before the rules ruled, when the not-worth-taking shelf is asked, then it holds it`() {
        // arrange
        val job = Job(1_000, pizza.copy(ruling = null), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)

        // act
        val held = Shelf.NOT_WORTH_TAKING.holds(job)

        // assert
        assertEquals(true, held)
    }

    @Test
    fun `given the pickup screen for a job on the board, when it is applied, then that job is taken`() {
        // arrange
        val board = listOf(
            Job(2_000, kebab, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
            Job(1_000, pizza.copy(pickup = "9 Guzman y Gomez (Dingley Village)"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
        )
        val pickup = Pickup(
            store = "Guzman y Gomez",
            address = "278 Centre Dandenong Rd, Dingley Village VIC 3172, Australia",
            note = "Dedicated GYG car park",
            orders = 1,
        )

        // act
        val next = JobBoard.taken(board, pickup, now = 2_000)

        // affirm  the other job is untouched
        assertEquals(false, next[0].taken)

        // assert
        assertEquals(
            Triple(true, pickup.address, pickup.note),
            Triple(next[1].taken, next[1].address, next[1].note),
        )
    }

    @Test
    fun `given a batched offer's second delivery, when its screen is read, then it is kept against the job in hand`() {
        // arrange  22 Sept 16:22: one card, "Delivery (2)", naming Keysborough only
        val job = Job(
            1_000, pizza.copy(pickup = "Oporto (Springvale)", dropoff = "Linden Drive & Rosette Crescent, Keysborough"),
            taken = true, address = "17 Springvale road, Springvale", note = null,
            dropAddress = "4 Rosette Cct, Keysborough VIC", dropUnit = null, dropNote = null,
            noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1,
        )
        val second = Dropoff(customer = "Rocco T.", address = "2-4 Hutton Street, Dandenong Melbourne VIC", unit = "11", note = "Meet outside")

        // act
        val next = JobBoard.alsoDelivered(listOf(job), second, now = 2_000)

        // assert
        assertEquals(listOf(Drop("2-4 Hutton Street, Dandenong Melbourne VIC", "11", "Meet outside")), next[0].extraDrops)
    }

    @Test
    fun `given both drops of a batch in one suburb, when the second screen is read, then the first is kept`() {
        // arrange  23 Sept 18:43: two orders from Nene Chicken, both to Noble Park
        val job = Job(
            1_000, pizza.copy(pickup = "Nene Chicken (Parkmore)", dropoff = "Prior Road & Temple Court, Noble Park"),
            taken = true, address = "317 Cheltenham Road, Keysborough VIC", note = null,
            dropAddress = "50 Prior Road, Noble Park Melbourne VIC", dropUnit = null, dropNote = "Ring the bell",
            noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 2,
        )
        val second = Dropoff(customer = "jojey L.", address = "8 Temple Court, Noble Park Melbourne VIC", unit = null, note = null)

        // act
        val next = JobBoard.delivered(listOf(job), second, suburb = "Noble Park Melbourne VIC", now = 2_000)

        // affirm  the first customer is still there, address and note
        assertEquals("50 Prior Road, Noble Park Melbourne VIC" to "Ring the bell", next[0].dropAddress to next[0].dropNote)

        // assert
        assertEquals(listOf(Drop("8 Temple Court, Noble Park Melbourne VIC", null, null)), next[0].extraDrops)
    }

    @Test
    fun `given a drop already on the job, when its screen is read again, then nothing is added`() {
        // arrange  the same screen is read every couple of seconds
        val job = Job(
            1_000, pizza.copy(dropoff = "Prior Road & Temple Court, Noble Park"),
            taken = true, address = null, note = null,
            dropAddress = "50 Prior Road, Noble Park Melbourne VIC", dropUnit = null, dropNote = "Ring the bell",
            noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 2,
        )
        val again = Dropoff(customer = null, address = "50 Prior Road, Noble Park", unit = null, note = null)

        // act
        val next = JobBoard.delivered(listOf(job), again, suburb = "Noble Park", now = 2_000)

        // assert
        assertEquals(emptyList<Drop>(), next[0].extraDrops)
    }

    @Test
    fun `given the same second delivery read twice, when it is applied again, then it is kept once`() {
        // arrange
        val drop = Drop("2-4 Hutton Street, Dandenong Melbourne VIC", "11", null)
        val job = Job(
            1_000, pizza.copy(pickup = "Oporto (Springvale)"), taken = true, address = "17 Springvale road", note = null,
            dropAddress = "4 Rosette Cct, Keysborough VIC", dropUnit = null, dropNote = null,
            noteCn = null, dropNoteCn = null, extraDrops = listOf(drop), ordersAtPickup = 2,
        )

        // act
        val next = JobBoard.alsoDelivered(listOf(job), Dropoff(null, drop.address, drop.unit, null), now = 2_000)

        // assert
        assertEquals(1, next[0].extraDrops.size)
    }

    @Test
    fun `given two jobs in hand, when a delivery screen names neither suburb, then nothing is claimed`() {
        // arrange  which of the two it belongs to cannot be told, and guessing sends him to the wrong door
        val one = Job(1_000, pizza, taken = true, address = null, note = null, dropAddress = "1 A St, Noble Park", dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)
        val two = Job(1_500, kebab, taken = true, address = null, note = null, dropAddress = "2 B St, Keysborough", dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)

        // act
        val next = JobBoard.alsoDelivered(listOf(two, one), Dropoff(null, "9 C St, Dandenong", null, null), now = 2_000)

        // assert
        assertEquals(listOf(two, one), next)
    }

    @Test
    fun `given a second order from the shop already being collected, when its pickup screen arrives, then the second job is taken`() {
        // arrange  21 Sept 16:59: the first Coles order was collected, the second added at the same counter
        val first = Job(1_000, pizza.copy(pickup = "Coles - Keysborough", dropoff = "13 Everitt St, Dandenong"), taken = true, address = "317 Cheltenham Rd, Keysborough 3173", note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)
        val second = Job(2_000, kebab.copy(pickup = "Coles - Keysborough", dropoff = "Alma Crescent & Cyril Grove, Noble Park"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1)
        val pickup = Pickup(store = "Coles - Keysborough", address = "317 Cheltenham Rd, Keysborough 3173", note = "Enter the car park via Kingsclere Avenue", orders = 1)

        // act
        val next = JobBoard.taken(listOf(second, first), pickup, now = 2_500)

        // affirm  the first is left as it was
        assertEquals(first, next[1])

        // assert
        assertEquals(true, next[0].taken)
    }

    @Test
    fun `given a card whose shop name OCR cut short, when the full name's pickup arrives, then that job is taken`() {
        // arrange  the card and pickup screen of 13 Sept 18:56
        val board = listOf(
            Job(1_000, pizza.copy(pickup = "Chemist2u) Pharmacy 4 Less"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
        )
        val pickup = Pickup(
            store = "(Chemist2U) Pharmacy 4 Less Parkmore",
            address = "Shop J01, 317-321 Cheltenham Road, Keysborough South, APAC 3173",
            note = null,
            orders = 1,
        )

        // act
        val next = JobBoard.taken(board, pickup, now = 2_000)

        // assert
        assertEquals(true, next[0].taken)
    }

    @Test
    fun `given a short shop name on a card, when a longer name containing it is picked up, then nothing is claimed`() {
        // arrange
        val board = listOf(Job(1_000, pizza.copy(pickup = "KFC"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))
        val pickup = Pickup(store = "Nashville KFC Style Chicken", address = "1 High St, Braeside VIC 3195", note = null, orders = 1)

        // act
        val next = JobBoard.taken(board, pickup, now = 2_000)

        // assert
        assertEquals(board, next)
    }

    @Test
    fun `given a pickup for a shop not on the board, when it is applied, then nothing changes`() {
        // arrange
        val board = listOf(Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))
        val pickup = Pickup(store = "Nando's", address = "1 High St, Braeside VIC 3195", note = null, orders = 1)

        // act
        val next = JobBoard.taken(board, pickup, now = 2_000)

        // assert
        assertEquals(board, next)
    }

    @Test
    fun `given two offers from one chain, when the pickup names a branch, then the matching branch is taken`() {
        // arrange  the newer offer is the other branch; the accepted one is older
        val board = listOf(
            Job(2_000, pizza.copy(pickup = "Guzman y Gomez (Springvale)"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
            Job(1_000, kebab.copy(pickup = "9 Guzman y Gomez (Dingley Village)"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
        )
        val pickup = Pickup(
            store = "Guzman y Gomez",
            address = "278 Centre Dandenong Rd, Dingley Village VIC 3172, Australia",
            note = null,
            orders = 1,
        )

        // act
        val next = JobBoard.taken(board, pickup, now = 2_000)

        // affirm  the newer one, from the wrong branch, is left alone
        assertEquals(false, next[0].taken)

        // assert
        assertEquals(true, next[1].taken)
    }

    @Test
    fun `given a shop the card names by its shopping centre, when the pickup arrives, then that job is taken`() {
        // arrange  the centre is Parkmore; the street it stands on is in Keysborough
        val card = pizza.copy(pickup = "Pizza Hut (Parkmore)", dropoff = "Bevan Avenue, Clayton South")
        val board = listOf(Job(1_000, card, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))

        // act
        val next = JobBoard.taken(
            board,
            Pickup(
                store = "Pizza Hut",
                address = "317 Cheltenham Rd, Keysborough VIC 3173, Australia",
                note = null,
            orders = 1,
        ),
            now = 2_000,
        )

        // affirm  the shop's real address is on the job now
        assertEquals("317 Cheltenham Rd, Keysborough VIC 3173, Australia", next.first().address)

        // assert
        assertEquals(true, next.first().taken)
    }

    @Test
    fun `given two branches of one chain and an address naming neither, when the pickup arrives, then nothing is claimed`() {
        // arrange
        val here = pizza.copy(pickup = "Pizza Hut (Parkmore)", dropoff = "Bevan Avenue, Clayton South")
        val there = pizza.copy(pickup = "Pizza Hut (Southland)", dropoff = "Como Parade, Mentone")
        val board = listOf(
            Job(2_000, here, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
            Job(1_000, there, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
        )

        // act
        val next = JobBoard.taken(
            board,
            Pickup(store = "Pizza Hut", address = "9 Smith St, Cheltenham VIC 3192, Australia", note = null, orders = 1),
            now = 2_000,
        )

        // assert  guessing would send him to the wrong branch
        assertEquals(board, next)
    }

    @Test
    fun `given a card that names no branch, when the pickup arrives, then the name alone is enough`() {
        // arrange
        val board = listOf(
            Job(1_000, pizza.copy(pickup = "Guzman y Gomez"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
        )
        val pickup = Pickup(
            store = "Guzman y Gomez",
            address = "278 Centre Dandenong Rd, Dingley Village VIC 3172, Australia",
            note = null,
            orders = 1,
        )

        // act
        val next = JobBoard.taken(board, pickup, now = 2_000)

        // assert
        assertEquals(true, next[0].taken)
    }

    @Test
    fun `given the delivery screen, when it is applied, then the job to that suburb gets the real address`() {
        // arrange
        val board = listOf(
            Job(2_000, kebab.copy(dropoff = "Corrigan Road, Noble Park"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
            Job(1_000, pizza.copy(dropoff = "Collins Street, Mentone"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
        )
        val dropoff = Dropoff(
            customer = "Lewis A.",
            address = "12/98 Collins St, Mentone VIC",
            unit = "Unit 12",
            note = "Buzz 12 on the intercom.",
        )

        // act
        val next = JobBoard.delivered(board, dropoff, "Mentone", now = 2_000)

        // affirm  the other job is untouched
        assertEquals(null, next[0].dropAddress)

        // assert
        assertEquals("12/98 Collins St, Mentone VIC", next[1].dropAddress)
    }

    @Test
    fun `given two jobs to one suburb, when a delivery arrives, then the one already taken wins`() {
        // arrange
        val board = listOf(
            Job(2_000, kebab.copy(dropoff = "Elsewhere, Mentone"), taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
            Job(1_000, pizza.copy(dropoff = "Collins Street, Mentone"), taken = true, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1),
        )
        val dropoff = Dropoff("Lewis A.", "12/98 Collins St, Mentone VIC", null, null)

        // act
        val next = JobBoard.delivered(board, dropoff, "Mentone", now = 2_000)

        // affirm
        assertEquals(null, next[0].dropAddress)

        // assert
        assertEquals("12/98 Collins St, Mentone VIC", next[1].dropAddress)
    }

    @Test
    fun `given a delivery to a suburb no job is going to, when it is applied, then nothing changes`() {
        // arrange
        val board = listOf(Job(1_000, pizza, taken = false, address = null, note = null, dropAddress = null, dropUnit = null, dropNote = null, noteCn = null, dropNoteCn = null, extraDrops = emptyList(), ordersAtPickup = 1))
        val dropoff = Dropoff("Lewis A.", "12/98 Collins St, Mentone VIC", null, null)

        // act
        val next = JobBoard.delivered(board, dropoff, "Mentone", now = 2_000)

        // assert
        assertEquals(board, next)
    }
}
